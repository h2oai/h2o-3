#'
#' Anonymous usage telemetry for h2o-r.
#'
#' Sends a single best-effort HTTPS POST at session start -- h2o.init() (local
#' server spawn) or h2o.connect() / attach -- describing the client: H2O and
#' runtime versions, OS, CPU arch, Java version/vendor, and coarse cluster-shape
#' buckets. Nothing is sent for per-operation activity (train / score / import /
#' download / save / ...); R telemetry is limited to the two session-start
#' events. R is single-threaded with no reliable background pump, so a
#' fire-and-forget scheme drops exactly the session-start events that matter
#' most; restricting to init/connect lets us deliver them reliably instead.
#'
#' Delivery is therefore a synchronous, bounded curl POST (the same HTTP library
#' communication.R uses). Because it only fires on the already-slow connect /
#' JVM-start path, the short timeout is invisible to the user, yet -- unlike a
#' fire-and-forget pass -- the event actually gets delivered. An unreachable
#' receiver never blocks, raises, or delays the caller beyond the timeout. The
#' `java -version` probe likewise runs detached (see warm_java); nothing on a
#' telemetry path ever waits on a subprocess or socket beyond that timeout.
#'
#' Honors the DO_NOT_TRACK opt-out environment variable (consoledonottrack.com),
#' which always wins. Reacts to 1/0/true/false; 0/false/empty do not opt out.
#'
#' URL override: H2O_TELEMETRY_URL.

# Production endpoint. Override via the H2O_TELEMETRY_URL environment variable.
.h2o.telemetry.url <- "https://telemetry.h2o.ai/v1/event"
.h2o.telemetry.payload_version <- 1L
.h2o.telemetry.timeout_secs <- 2L
.h2o.telemetry.max_version_len <- 64L

# Build-flavor attribution (OSS vs Enterprise), hardcoded per repo at build
# time. This is the OSS repo. Mirrors h2o-py/h2o/_product.py.
.h2o.telemetry.product <- "h2o-3-oss"

# Per-process shared session_id + caches in a private env so they persist
# across function calls without leaking package globals.
#
# disabled_by_kwarg starts at the client-wide default when the user never passes
# the telemetry arg: TRUE = telemetry off (opt-in model) — a bare h2o.init()
# stays off until the user opts in (telemetry = TRUE, h2o.set_telemetry(TRUE),
# or a persisted/config opt-in). Flip to FALSE for the opt-out model (default-on).
.h2o.telemetry.state <- new.env(parent = emptyenv())
.h2o.telemetry.state$session_id        <- NULL
.h2o.telemetry.state$java_info         <- NULL  # cached `java -version` parse
.h2o.telemetry.state$disabled_by_kwarg <- TRUE  # opt-in default: off until the user opts in

#' Programmatic opt-out, set by `h2o.init(telemetry = FALSE)`.
#'
#' Once disabled, every subsequent `.h2o.send_*` call is a no-op until the
#' next R session. Independent of the `DO_NOT_TRACK` env-var opt-out, which
#' always wins.
#' @keywords internal
.h2o.telemetry.set_disabled <- function(disabled) {
  .h2o.telemetry.state$disabled_by_kwarg <- isTRUE(disabled)
  invisible(NULL)
}

.h2o.telemetry.resolve_url <- function() {
  envv <- Sys.getenv("H2O_TELEMETRY_URL")
  if (nzchar(envv)) return(envv)
  .h2o.telemetry.url
}

# TRUE iff env var `name` is set to a truthy value. Reacts to 1/0/true/false;
# 0/false/no/off/empty/unset all read as FALSE (so DO_NOT_TRACK=0 does NOT opt out).
.h2o.telemetry.env_truthy <- function(name) {
  v <- tolower(trimws(Sys.getenv(name)))
  nzchar(v) && !(v %in% c("0", "false", "no", "off"))
}

.h2o.telemetry.disabled <- function() {
  # DO_NOT_TRACK (cross-tool standard) is the hard opt-out and always wins.
  if (.h2o.telemetry.env_truthy("DO_NOT_TRACK")) return(TRUE)
  isTRUE(.h2o.telemetry.state$disabled_by_kwarg)
}

# --- Persistent opt-out & preference sources (kept consistent with the Python client) ---

# Resolve the user home dir the SAME way Python (os.path.expanduser) and the JVM
# (user.home) do -- USERPROFILE on Windows -- so R agrees with them. R's own
# path.expand("~") points at Documents on Windows, which would diverge.
.h2o.telemetry.home_dir <- function() {
  if (.Platform$OS.type == "windows") {
    up <- Sys.getenv("USERPROFILE"); if (nzchar(up)) return(up)
    hd <- Sys.getenv("HOMEDRIVE"); hp <- Sys.getenv("HOMEPATH")
    if (nzchar(hd) && nzchar(hp)) return(paste0(hd, hp))
  }
  h <- Sys.getenv("HOME"); if (nzchar(h)) return(h)
  path.expand("~")
}

.h2o.telemetry.config_dir <- function() file.path(.h2o.telemetry.home_dir(), ".h2oai")
.h2o.telemetry.pref_file  <- function() file.path(.h2o.telemetry.config_dir(), "telemetry")

# Parse a persisted/config flag: TRUE, FALSE, or NULL if unrecognized.
.h2o.telemetry.parse_bool <- function(s) {
  if (length(s) == 0L || is.na(s[1L])) return(NULL)
  s <- tolower(trimws(s[1L]))
  if (s %in% c("1", "true", "yes", "on")) return(TRUE)
  if (s %in% c("0", "false", "no", "off")) return(FALSE)
  NULL
}

# On/off from ~/.h2oai/telemetry (written by h2o.set_telemetry). NULL if unset.
.h2o.telemetry.file_pref <- function() {
  tryCatch({
    f <- .h2o.telemetry.pref_file()
    if (!file.exists(f)) return(NULL)
    .h2o.telemetry.parse_bool(readLines(f, n = 1, warn = FALSE))
  }, error = function(e) NULL)
}

# On/off from ~/.h2oconfig (home only). NULL if unset/unreadable. Recognizes
# `telemetry = <bool>` under [general] (or no section) or `general.telemetry`.
# Home-only by design: a privacy opt-out must not depend on the working dir
# (unlike the connection keys the config reader walks up from cwd).
.h2o.telemetry.config_pref <- function() {
  tryCatch({
    path <- file.path(.h2o.telemetry.home_dir(), ".h2oconfig")
    if (!file.exists(path)) return(NULL)
    section <- ""; found <- NULL
    for (line in readLines(path, warn = FALSE)) {
      line <- trimws(line)
      if (!nzchar(line) || substring(line, 1L, 1L) == "#") next
      if (grepl("^\\[.*\\]$", line)) {
        section <- tolower(trimws(sub("^\\[(.*)\\]$", "\\1", line))); next
      }
      pos <- regexpr("=", line, fixed = TRUE)
      if (pos < 1L) next
      key <- tolower(trimws(substr(line, 1L, pos - 1L)))
      raw <- trimws(substr(line, pos + 1L, nchar(line)))
      if (grepl(":", key, fixed = TRUE)) {          # strip a py:/r: language prefix
        parts <- strsplit(key, ":", fixed = TRUE)[[1L]]
        if (!identical(parts[1L], "r")) next          # r: applies to the R client
        key <- parts[2L]
      }
      if (identical(key, "general.telemetry") ||
          (identical(key, "telemetry") && section %in% c("", "general"))) {
        found <- .h2o.telemetry.parse_bool(raw)
      }
    }
    found
  }, error = function(e) NULL)
}

# Seed the initial opt-out state (called from .onLoad) from the static opt-out
# surfaces: ~/.h2oconfig (home) and ~/.h2oai/telemetry. Any opt-out wins (union,
# matching DO_NOT_TRACK); else an opt-in applies; else the default is kept.
.h2o.telemetry.load_persisted_pref <- function() {
  tryCatch({
    prefs <- list(.h2o.telemetry.config_pref(), .h2o.telemetry.file_pref())
    if (any(vapply(prefs, function(p) identical(p, FALSE), logical(1L)))) {
      .h2o.telemetry.state$disabled_by_kwarg <- TRUE
    } else if (any(vapply(prefs, function(p) identical(p, TRUE), logical(1L)))) {
      .h2o.telemetry.state$disabled_by_kwarg <- FALSE
    }
  }, error = function(e) invisible(NULL))
  invisible(NULL)
}

#' Enable or disable anonymous client telemetry and persist the choice.
#'
#' Applies immediately for this R session and is remembered across sessions
#' (stored under \code{~/.h2oai}). The \code{DO_NOT_TRACK} environment variable
#' still overrides it. Best-effort and silent: never errors, never prints.
#'
#' @param enabled \code{TRUE} to enable telemetry, \code{FALSE} to opt out.
#' @return (invisibly) \code{TRUE} if the preference was written to disk, else \code{FALSE}.
#' @export
h2o.set_telemetry <- function(enabled) {
  .h2o.telemetry.set_disabled(!isTRUE(enabled))
  ok <- tryCatch({
    d <- .h2o.telemetry.config_dir()
    dir.create(d, showWarnings = FALSE, recursive = TRUE)
    writeLines(if (isTRUE(enabled)) "1" else "0", file.path(d, "telemetry"))
    TRUE
  }, error = function(e) FALSE)
  invisible(ok)
}

#' Report whether anonymous client telemetry is currently enabled.
#'
#' @return \code{TRUE} if telemetry is enabled, otherwise \code{FALSE}.
#' @export
h2o.telemetry_enabled <- function() {
  !.h2o.telemetry.disabled()
}

# Generate a random UUIDv4 from 16 bytes — avoids depending on the uuid package.
# Save and restore .Random.seed so telemetry never perturbs the user's RNG
# stream (otherwise enabling/disabling telemetry would change reproducible results).
.h2o.telemetry.uuid <- function() {
  if (exists(".Random.seed", envir = .GlobalEnv, inherits = FALSE)) {
    old_seed <- get(".Random.seed", envir = .GlobalEnv, inherits = FALSE)
    on.exit(assign(".Random.seed", old_seed, envir = .GlobalEnv), add = TRUE)
  } else {
    # RNG was never initialized in this session; remove the seed we are about
    # to create so the user's stream stays in its pristine, uninitialized state.
    on.exit(suppressWarnings(rm(".Random.seed", envir = .GlobalEnv)), add = TRUE)
  }
  b <- as.integer(sample.int(256L, 16L, replace = TRUE) - 1L)
  b[7]  <- bitwOr(bitwAnd(b[7],  0x0F), 0x40)
  b[9]  <- bitwOr(bitwAnd(b[9],  0x3F), 0x80)
  hex <- sprintf("%02x", b)
  paste0(
    paste(hex[1:4],  collapse = ""), "-",
    paste(hex[5:6],  collapse = ""), "-",
    paste(hex[7:8],  collapse = ""), "-",
    paste(hex[9:10], collapse = ""), "-",
    paste(hex[11:16], collapse = "")
  )
}

.h2o.telemetry.new_session_id <- function() {
  sid <- .h2o.telemetry.uuid()
  .h2o.telemetry.state$session_id <- sid
  sid
}

.h2o.telemetry.current_session_id <- function() {
  sid <- .h2o.telemetry.state$session_id
  if (is.null(sid)) sid <- .h2o.telemetry.new_session_id()
  sid
}

.h2o.telemetry.os <- function() {
  sysname <- tolower(Sys.info()[["sysname"]])
  if (sysname == "darwin") return("macos")
  if (sysname == "windows") return("windows")
  if (sysname == "linux") return("linux")
  sysname
}

.h2o.telemetry.str <- function(x) {
  if (is.null(x)) return("")
  s <- tryCatch(as.character(x), error = function(e) "")
  if (length(s) == 0L) return("")
  s <- s[[1]]
  if (is.na(s)) return("")
  s
}

.h2o.telemetry.cap_version <- function(value, max_len = .h2o.telemetry.max_version_len) {
  if (is.null(value)) return(NULL)
  s <- as.character(value)
  if (length(s) == 0L) return(NULL)
  s <- s[[1]]
  if (is.na(s) || !nzchar(s)) return(NULL)
  if (nchar(s) > max_len) substr(s, 1L, max_len) else s
}

# -- cluster-shape bucket helpers. The label strings must stay byte-identical
# -- across the Python / R / JVM clients. Mirrors h2o-py/h2o/telemetry.py.
# -- Only the two session-start events (init / cluster_connect) emit, so just
# -- the cluster-shape buckets remain; per-operation buckets are unused in R.

bucketize_cluster_nodes <- function(n) {
  # Capture fine, display coarse: exact node count for n <= 16, buckets above.
  n <- as.integer(n)
  if (n <= 16)  return(as.character(n))
  if (n <= 20)  return("17-20")
  if (n <= 24)  return("21-24")
  if (n <= 32)  return("25-32")
  if (n <= 48)  return("33-48")
  if (n <= 64)  return("49-64")
  if (n <= 128) return("65-128")
  if (n <= 256) return("129-256")
  ">256"
}

bucketize_cluster_memory_gb <- function(gb) {
  # Doubling scale matching how RAM physically ships; floor at "<4".
  if (gb < 4)    return("<4")
  if (gb < 8)    return("4-8")
  if (gb < 16)   return("8-16")
  if (gb < 32)   return("16-32")
  if (gb < 64)   return("32-64")
  if (gb < 128)  return("64-128")
  if (gb < 256)  return("128-256")
  if (gb < 512)  return("256-512")
  if (gb < 1024) return("512-1024")
  if (gb < 2048) return("1024-2048")
  if (gb < 4096) return("2048-4096")
  ">4096"
}

# -- Runtime version detection --

.h2o.telemetry.r_version <- function() {
  tryCatch(
    .h2o.telemetry.cap_version(
      paste(R.version$major, R.version$minor, sep = ".")
    ),
    error = function(e) NULL
  )
}

# Map R.version$arch to the cpu_arch value. To mirror the Python client's
# OS-native arm64/aarch64 split, Apple-Silicon macOS (which R reports as
# "aarch64") is reported as "arm64"; Linux ARM64 stays "aarch64".
.h2o.telemetry.cpu_arch <- function() {
  arch <- tryCatch(tolower(R.version$arch), error = function(e) "")
  sysname <- tryCatch(tolower(Sys.info()[["sysname"]]), error = function(e) "")
  if (length(arch) == 0L || is.na(arch)) arch <- ""
  if (arch %in% c("x86_64", "amd64")) return("x86_64")
  if (grepl("aarch64|arm64", arch)) {
    if (identical(sysname, "darwin")) return("arm64")
    return("aarch64")
  }
  if (grepl("ppc64", arch)) return("ppc64le")
  if (identical(arch, "s390x")) return("s390x")
  "other"
}

# Resolve the java binary the same way H2O does when launching a local server
# (.h2o.checkJava honors JAVA_HOME, then scans the OS). Returns NULL rather than
# invoking the macOS /usr/bin/java stub, which pops an "install a JDK" dialog
# when no JDK is present — telemetry must never produce user-visible side effects.
.h2o.telemetry.resolve_java <- function() {
  java_bin <- tryCatch(.h2o.checkJava(), error = function(e) NULL)
  if (is.null(java_bin) || !nzchar(java_bin)) return(NULL)
  if (!nzchar(Sys.getenv("JAVA_HOME")) &&
      identical(Sys.info()[["sysname"]], "Darwin") &&
      identical(normalizePath(java_bin, mustWork = FALSE), "/usr/bin/java")) {
    jh <- tryCatch(suppressWarnings(system2("/usr/libexec/java_home",
                                            stdout = TRUE, stderr = FALSE)),
                   error = function(e) character(0))
    jh <- jh[nzchar(jh)]
    if (length(jh) == 0L) return(NULL)
    cand <- file.path(jh[[1L]], "bin", "java")
    return(if (file.exists(cand)) cand else NULL)
  }
  java_bin
}

# Per-session file the detached `java -version` probe writes to, and that
# detect_java() later reads. One fixed name per R session (tempdir() is private
# to this session), so warm_java is naturally idempotent via file.exists.
.h2o.telemetry.java_cache_file <- function() {
  file.path(tempdir(), "h2o_telemetry_java.txt")
}

#' Kick off a *detached* `java -version` probe whose output detect_java() parses
#' later. Non-blocking: system2(wait = FALSE) returns immediately, and the child
#' runs concurrently with the (slow) connect / JVM-start so its result is
#' usually ready by the time the session-start event fires. Called early in
#' h2o.init() / h2o.connect(). Never blocks, never raises.
#' @keywords internal
.h2o.telemetry.warm_java <- function() {
  if (!is.null(.h2o.telemetry.state$java_info)) return(invisible(NULL))
  cache <- .h2o.telemetry.java_cache_file()
  if (file.exists(cache)) return(invisible(NULL))  # already probed / in flight
  java_bin <- tryCatch(.h2o.telemetry.resolve_java(), error = function(e) NULL)
  if (is.null(java_bin) || !nzchar(java_bin)) return(invisible(NULL))
  # `java -version` and -XshowSettings both write to stderr; capture it to the
  # cache file. wait = FALSE => the user's session does not block on the probe.
  tryCatch(
    system2(java_bin, args = c("-XshowSettings:properties", "-version"),
            stdout = FALSE, stderr = cache, wait = FALSE),
    error = function(e) invisible(NULL))
  invisible(NULL)
}

# Read + parse the warm_java cache file. Pure file I/O — never spawns or waits
# on a subprocess, so it is safe on any telemetry path. If the probe hasn't been
# started or hasn't finished writing, returns empty info (java fields omitted on
# this event) and is re-attempted on the next event; only memoizes once a
# version was actually parsed (i.e. the file is complete).
.h2o.telemetry.detect_java <- function() {
  if (!is.null(.h2o.telemetry.state$java_info)) {
    return(.h2o.telemetry.state$java_info)
  }
  info <- list(version = NULL, vendor = NULL)
  cache <- .h2o.telemetry.java_cache_file()
  if (!file.exists(cache)) {
    # Probe never started (e.g. an event fired before init's warm-up) — start
    # it now for next time, but never wait on it.
    .h2o.telemetry.warm_java()
    return(info)
  }
  tryCatch({
    text <- paste(readLines(cache, warn = FALSE), collapse = "\n")
    m_ver <- regmatches(text, regexpr("java\\.version\\s*=\\s*[^\n]+", text))
    if (length(m_ver) == 1L && nzchar(m_ver)) {
      info$version <- trimws(sub("^java\\.version\\s*=\\s*", "", m_ver))
    } else {
      # Fallback: parse the `version "X.Y.Z"` line.
      m_ver2 <- regmatches(text, regexpr('version\\s+"[^"]+"', text))
      if (length(m_ver2) == 1L && nzchar(m_ver2)) {
        info$version <- gsub('.*"([^"]+)".*', "\\1", m_ver2)
      }
    }
    m_vend <- regmatches(text, regexpr("java\\.vendor\\s*=\\s*[^\n]+", text))
    if (length(m_vend) == 1L && nzchar(m_vend)) {
      info$vendor <- trimws(sub("^java\\.vendor\\s*=\\s*", "", m_vend))
    }
  }, error = function(e) invisible(NULL))
  # Only memoize once the file is complete (version parsed); otherwise let a
  # later event re-read it after the detached probe has finished writing.
  if (!is.null(info$version)) .h2o.telemetry.state$java_info <- info
  info
}

.h2o.telemetry.java_version <- function() {
  .h2o.telemetry.cap_version(.h2o.telemetry.detect_java()$version)
}

.h2o.telemetry.java_vendor <- function() {
  .h2o.telemetry.cap_version(.h2o.telemetry.detect_java()$vendor)
}

# -- Cluster topology derivation (mirror of Python `_derive_cluster_topology`) --

.h2o.telemetry.derive_topology <- function(cloud_size, hadoop_version = NULL) {
  n <- tryCatch(as.integer(cloud_size), error = function(e) 0L)
  if (is.na(n)) n <- 0L
  if (n == 1L) return("single_node")
  if (!is.null(hadoop_version) && nzchar(hadoop_version)) return("multi_node_hadoop")
  if (nzchar(Sys.getenv("KUBERNETES_SERVICE_HOST"))) return("kubernetes")
  if (nzchar(Sys.getenv("HADOOP_HOME")) ||
      nzchar(Sys.getenv("HADOOP_CONF_DIR")) ||
      nzchar(Sys.getenv("HADOOP_PREFIX"))) return("multi_node_hadoop")
  if (n > 1L) return("multi_node_standalone")
  "unknown"
}

#' Read cluster shape from the live h2o connection. Returns a list with
#' nullable keys cluster_nodes_bucket / cluster_memory_gb_bucket /
#' cluster_topology. Never raises.
#' @keywords internal
.h2o.telemetry.derive_cluster_shape <- function() {
  out <- list(cluster_nodes_bucket = NULL,
              cluster_memory_gb_bucket = NULL,
              cluster_topology = NULL)
  tryCatch({
    # Read CloudV3 directly. h2o.clusterStatus()/clusterInfo() print to the
    # console and reshape the response into a per-node data.frame (no
    # $cloud_size), so they are unusable for silent telemetry.
    info <- tryCatch(
      .h2o.fromJSON(jsonlite::fromJSON(.h2o.doSafeGET(urlSuffix = .h2o.__CLOUD),
                                       simplifyDataFrame = FALSE)),
      error = function(e) NULL)
    if (is.null(info)) return(out)
    cloud_size <- tryCatch(as.integer(info$cloud_size %||% NA), error = function(e) NA_integer_)
    if (length(cloud_size) > 1L) cloud_size <- cloud_size[[1L]]
    if (!is.na(cloud_size) && cloud_size > 0L) {
      out$cluster_nodes_bucket <- bucketize_cluster_nodes(cloud_size)
    }
    # Total cluster memory = sum of per-node JVM heap ceilings (max_mem, bytes).
    nodes <- info$nodes %||% NULL
    if (!is.null(nodes) && length(nodes) > 0L) {
      total <- tryCatch(
        sum(vapply(nodes, function(n) as.numeric(n$max_mem %||% 0), numeric(1L)), na.rm = TRUE),
        error = function(e) 0)
      if (total > 0) {
        out$cluster_memory_gb_bucket <- bucketize_cluster_memory_gb(total / (1024^3))
      }
    }
    # Server-side Hadoop signal trumps client-side env-var heuristics — a
    # workstation client connecting to a Hadoop-launched cluster has no
    # HADOOP_HOME locally, but the cluster reports its own provenance via
    # the hadoop_version field on CloudV3 (populated from -ga_hadoop_ver).
    hv <- info$hadoop_version %||% NULL
    if (!is.null(hv) && (is.na(hv) || !nzchar(as.character(hv)))) hv <- NULL
    out$cluster_topology <- .h2o.telemetry.derive_topology(cloud_size, hadoop_version = hv)
  }, error = function(e) invisible(NULL))
  out
}

`%||%` <- function(a, b) if (is.null(a)) b else a

# -- Common envelope shared by all events --

.h2o.telemetry.envelope <- function(h2o_version) {
  list(
    payload_version = .h2o.telemetry.payload_version,
    client          = "r",
    h2o_version     = .h2o.telemetry.str(h2o_version),
    os              = .h2o.telemetry.os(),
    os_version      = .h2o.telemetry.str(Sys.info()[["release"]]),
    session_id      = .h2o.telemetry.current_session_id(),
    # floor(as.numeric(...)) not as.integer(...): epoch seconds overflow R's
    # 32-bit integer in 2038 (as.integer would yield NA), and jsonlite emits the
    # whole-valued double without a decimal point — matching the other clients.
    ts              = floor(as.numeric(Sys.time())),
    # build-flavor attribution (OSS vs Enterprise), on every event.
    product         = .h2o.telemetry.product
  )
}

.h2o.telemetry.strip_null <- function(x) Filter(Negate(is.null), x)

.h2o.telemetry.with_extras <- function(payload, extras = NULL, attributes = NULL) {
  if (!is.null(extras)) {
    extras <- .h2o.telemetry.strip_null(extras)
    if (length(extras) > 0L) payload <- c(payload, extras)
  }
  if (!is.null(attributes) && length(attributes) > 0L) {
    # attribute values must be strings.
    attrs <- lapply(attributes, function(v) if (is.null(v)) NULL else as.character(v)[[1]])
    attrs <- .h2o.telemetry.strip_null(attrs)
    if (length(attrs) > 0L) payload$attributes <- attrs
  }
  payload
}

# -- Delivery: one synchronous, bounded HTTPS POST via the curl package (the
# -- same HTTP library communication.R uses). Only the two session-start events
# -- (init / cluster_connect) reach this, and those already block on the
# -- connect / JVM-start path, so a short bounded POST is invisible to the user
# -- yet -- unlike a fire-and-forget pass on single-threaded R -- actually
# -- delivers the event. connecttimeout/timeout bound the wait; the whole call
# -- is wrapped so an unreachable receiver never blocks, raises, or delays the
# -- caller beyond the timeout.
.h2o.telemetry.post <- function(payload) {
  if (!requireNamespace("curl", quietly = TRUE)) return(invisible(NULL))
  tryCatch({
    body <- as.character(jsonlite::toJSON(payload, auto_unbox = TRUE, null = "null"))
    h <- curl::new_handle()
    curl::handle_setheaders(h, "Content-Type" = "application/json")
    curl::handle_setopt(h, post = TRUE, postfields = body,
                        connecttimeout = 1L, timeout = .h2o.telemetry.timeout_secs)
    curl::curl_fetch_memory(.h2o.telemetry.resolve_url(), handle = h)
  }, error = function(e) invisible(NULL))
  invisible(NULL)
}

.h2o.telemetry.send <- function(payload) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  tryCatch(.h2o.telemetry.post(payload), error = function(e) invisible(NULL))
  invisible(NULL)
}

# -- Public emitters ---------------------------------------------------------

.h2o.telemetry.runtime_fields <- function() {
  .h2o.telemetry.strip_null(list(
    python_version = NULL,                                # not applicable to R client
    r_version      = .h2o.telemetry.r_version(),
    java_version   = .h2o.telemetry.java_version(),
    java_vendor    = .h2o.telemetry.java_vendor(),
    cpu_arch       = .h2o.telemetry.cpu_arch(),           # on init / cluster_connect
    python_distribution = NULL                            # Python-specific; always null on R
  ))
}

# Build-flavor distribution marker ("h2o" full package vs "h2o_client").
# The marker file is baked per flavor at build time (see h2o-r/build.gradle);
# a source/dev install with no marker falls back to "h2o".
.h2o.telemetry.distribution <- function() {
  if (!is.null(.h2o.telemetry.state$distribution)) return(.h2o.telemetry.state$distribution)
  d <- tryCatch({
    f <- system.file("telemetry_distribution.txt", package = "h2o")
    if (nzchar(f) && file.exists(f)) trimws(readLines(f, n = 1L, warn = FALSE)) else "h2o"
  }, error = function(e) "h2o")
  if (length(d) != 1L || is.na(d) || !nzchar(d)) d <- "h2o"
  .h2o.telemetry.state$distribution <- d
  d
}

# Merge attributes.distribution onto the session-start events (init /
# cluster_connect), mirroring the Python client. A caller-supplied value wins.
.h2o.telemetry.attributes_with_distribution <- function(attributes) {
  attrs <- if (is.null(attributes)) list() else attributes
  if (is.null(attrs$distribution)) attrs$distribution <- .h2o.telemetry.distribution()
  attrs
}

# First-run disclosure notice: shown once per environment, then never again.
# Gated on a per-client marker under ~/.h2oai that records the notice version, so
# it reappears only if the disclosure changes -- never merely on an H2O upgrade.
.h2o.telemetry.notice_version <- 1L  # bump ONLY when the notice text / policy changes

.h2o.telemetry.notice_text <- function() paste(
  "H2O-3 collects anonymous usage telemetry (H2O version, OS, algorithm names, and",
  "coarse usage buckets) to help prioritize features and platforms. It never sends",
  "your code, data, file paths, or any identifiers.",
  "To opt out: set DO_NOT_TRACK=1, call h2o.set_telemetry(FALSE) (persistent),",
  "or pass telemetry = FALSE to h2o.init() / h2o.connect().",
  "Docs: https://docs.h2o.ai/h2o/latest-stable/h2o-docs/telemetry.html",
  "(This notice is shown only once.)",
  sep = "\n")

.h2o.telemetry.maybe_print_notice <- function() {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  tryCatch({
    marker <- file.path(.h2o.telemetry.config_dir(), ".telemetry_notice_r")
    seen <- tryCatch(suppressWarnings(as.integer(readLines(marker, n = 1, warn = FALSE)[1L])),
                     error = function(e) NA_integer_)
    if (!is.na(seen) && seen >= .h2o.telemetry.notice_version) return(invisible(NULL))
    message("\n", .h2o.telemetry.notice_text(), "\n")
    dir.create(dirname(marker), showWarnings = FALSE, recursive = TRUE)
    writeLines(as.character(.h2o.telemetry.notice_version), marker)
  }, error = function(e) invisible(NULL))
  invisible(NULL)
}

#' @keywords internal
.h2o.send_init_telemetry <- function(h2o_version, cluster_shape = NULL, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  .h2o.telemetry.maybe_print_notice()
  .h2o.telemetry.new_session_id()
  payload <- tryCatch({
    base <- c(list(event = "init"), .h2o.telemetry.envelope(h2o_version))
    extras <- c(.h2o.telemetry.runtime_fields(), .h2o.telemetry.strip_null(cluster_shape %||% list()))
    .h2o.telemetry.with_extras(base, extras = extras,
                               attributes = .h2o.telemetry.attributes_with_distribution(attributes))
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_cluster_connect_telemetry <- function(h2o_version, cluster_shape = NULL, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  .h2o.telemetry.maybe_print_notice()
  .h2o.telemetry.new_session_id()
  payload <- tryCatch({
    base <- c(list(event = "cluster_connect"), .h2o.telemetry.envelope(h2o_version))
    extras <- c(.h2o.telemetry.runtime_fields(), .h2o.telemetry.strip_null(cluster_shape %||% list()))
    .h2o.telemetry.with_extras(base, extras = extras,
                               attributes = .h2o.telemetry.attributes_with_distribution(attributes))
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}
