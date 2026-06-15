#'
#' Anonymous usage telemetry for h2o-r.
#'
#' Sends best-effort HTTPS POSTs to the telemetry endpoint describing client
#' activity. Delivery uses the `curl` package (the same HTTP library as the
#' rest of the R client, see communication.R): a synchronous POST with tight
#' `connecttimeout`/`timeout` limits, fully wrapped in tryCatch, so an
#' unreachable server costs at most a couple of seconds and never raises.
#'
#' Honors two opt-out environment variables (first match wins):
#'     H2O_DISABLE_TELEMETRY -- H2O-specific kill switch
#'     DO_NOT_TRACK          -- industry-standard opt-out
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
.h2o.telemetry.state <- new.env(parent = emptyenv())
.h2o.telemetry.state$session_id        <- NULL
.h2o.telemetry.state$java_info         <- NULL  # cached `java -version` parse
.h2o.telemetry.state$disabled_by_kwarg <- FALSE # programmatic opt-out via h2o.init(telemetry = FALSE)

#' Programmatic opt-out, set by `h2o.init(telemetry = FALSE)`.
#'
#' Once disabled, every subsequent `.h2o.send_*` call is a no-op until the
#' next R session. Independent of and additive to the env-var opt-outs
#' (`H2O_DISABLE_TELEMETRY` / `DO_NOT_TRACK`).
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

.h2o.telemetry.disabled <- function() {
  if (isTRUE(.h2o.telemetry.state$disabled_by_kwarg)) return(TRUE)
  nzchar(Sys.getenv("H2O_DISABLE_TELEMETRY")) ||
    nzchar(Sys.getenv("DO_NOT_TRACK"))
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

# -- Bucketize helpers. The label strings must stay byte-identical across the
# -- Python / R / JVM clients. Mirrors h2o-py/h2o/telemetry.py.

bucketize_duration_ms <- function(ms) {
  # Sub-second values floor to "<5s" — no sub-second resolution.
  seconds <- ms / 1000
  if (seconds < 5)      return("<5s")
  if (seconds < 15)     return("5s-15s")
  if (seconds < 30)     return("15s-30s")
  if (seconds < 60)     return("30s-1m")
  if (seconds < 120)    return("1m-2m")
  if (seconds < 300)    return("2m-5m")
  if (seconds < 600)    return("5m-10m")
  if (seconds < 900)    return("10m-15m")
  if (seconds < 1800)   return("15m-30m")
  if (seconds < 3600)   return("30m-1h")
  if (seconds < 7200)   return("1h-2h")
  if (seconds < 14400)  return("2h-4h")
  if (seconds < 21600)  return("4h-6h")
  ">6h"
}

bucketize_rows <- function(n) {
  if (n < 1000)      return("<1k")
  if (n < 3000)      return("1k-3k")
  if (n < 10000)     return("3k-10k")
  if (n < 30000)     return("10k-30k")
  if (n < 100000)    return("30k-100k")
  if (n < 300000)    return("100k-300k")
  if (n < 1000000)   return("300k-1M")
  if (n < 3000000)   return("1M-3M")
  if (n < 10000000)  return("3M-10M")
  if (n < 30000000)  return("10M-30M")
  if (n < 100000000) return("30M-100M")
  ">100M"
}

bucketize_cols <- function(n) {
  if (n < 10)      return("<10")
  if (n < 30)      return("10-30")
  if (n < 100)     return("30-100")
  if (n < 300)     return("100-300")
  if (n < 1000)    return("300-1k")
  if (n < 3000)    return("1k-3k")
  if (n < 10000)   return("3k-10k")
  if (n < 30000)   return("10k-30k")
  if (n < 100000)  return("30k-100k")
  if (n < 300000)  return("100k-300k")
  if (n < 1000000) return("300k-1M")
  ">1M"
}

# Three range-tuned size scales (MOJO, model artifact, dataset). All use MiB (1048576).

bucketize_mojo_size <- function(b) {
  mb <- b / 1048576
  if (mb < 0.1) return("<100KB")
  if (mb < 1)   return("100KB-1MB")
  if (mb < 5)   return("1MB-5MB")
  if (mb < 10)  return("5MB-10MB")
  if (mb < 50)  return("10MB-50MB")
  if (mb < 100) return("50MB-100MB")
  ">100MB"
}

bucketize_artifact_size <- function(b) {
  mb <- b / 1048576
  if (mb < 0.1)  return("<100KB")
  if (mb < 1)    return("100KB-1MB")
  if (mb < 10)   return("1MB-10MB")
  if (mb < 100)  return("10MB-100MB")
  if (mb < 1024) return("100MB-1GB")
  ">1GB"
}

bucketize_data_size <- function(b) {
  mb <- b / 1048576
  gb <- mb / 1024
  if (mb < 10)   return("<10MB")
  if (mb < 100)  return("10MB-100MB")
  if (mb < 500)  return("100MB-500MB")
  if (mb < 1024) return("500MB-1GB")
  if (gb < 5)    return("1GB-5GB")
  if (gb < 10)   return("5GB-10GB")
  if (gb < 50)   return("10GB-50GB")
  if (gb < 100)  return("50GB-100GB")
  if (gb < 250)  return("100GB-250GB")
  if (gb < 500)  return("250GB-500GB")
  if (gb < 1024) return("500GB-1TB")
  if (gb < 1536) return("1TB-1.5TB")
  if (gb < 2048) return("1.5TB-2TB")
  ">2TB"
}

# -- cluster-shape bucket helpers --

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

# frame_memory_gb_bucket shares boundaries with cluster_memory_gb_bucket.
bucketize_frame_memory_gb <- bucketize_cluster_memory_gb

bucketize_max_models <- function(n) {
  if (n < 10)  return("<10")
  if (n < 50)  return("10-50")
  if (n < 200) return("50-200")
  ">200"
}

bucketize_max_runtime_secs <- function(secs) {
  if (secs < 60)   return("<60s")
  if (secs < 600)  return("60s-10m")
  if (secs < 3600) return("10m-1h")
  ">1h"
}

bucketize_leaderboard_size <- function(n) {
  if (n < 10)  return("<10")
  if (n < 50)  return("10-50")
  if (n < 100) return("50-100")
  ">100"
}

# sort_metric must be one of this fixed lowercase set, and is required. H2O
# AutoML's own metric names map onto it case-insensitively, so we lowercase;
# anything unrecognized falls back to "auto" (AutoML's default) so an odd
# metric can never drop the event.
.h2o.telemetry.allowed_sort_metrics <- c(
  "auto", "deviance", "logloss", "rmse", "mse", "mae",
  "rmsle", "auc", "aucpr", "mean_per_class_error"
)

.h2o.telemetry.normalize_sort_metric <- function(metric) {
  if (!is.null(metric) && length(metric) > 0L && !is.na(metric)) {
    s <- tolower(trimws(as.character(metric)[[1L]]))
    if (s %in% .h2o.telemetry.allowed_sort_metrics) return(s)
  }
  "auto"
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

.h2o.telemetry.detect_java <- function() {
  if (!is.null(.h2o.telemetry.state$java_info)) {
    return(.h2o.telemetry.state$java_info)
  }
  info <- list(version = NULL, vendor = NULL)
  java_bin <- .h2o.telemetry.resolve_java()
  if (is.null(java_bin)) {
    .h2o.telemetry.state$java_info <- info
    return(info)
  }
  tryCatch({
    out <- suppressWarnings(system2(
      java_bin,
      args = c("-XshowSettings:properties", "-version"),
      stdout = TRUE, stderr = TRUE, timeout = 2
    ))
    text <- paste(out, collapse = "\n")
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
  .h2o.telemetry.state$java_info <- info
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
    jvm_version     = .h2o.telemetry.str(.h2o.telemetry.java_version() %||% ""),
    session_id      = .h2o.telemetry.current_session_id(),
    ts              = as.integer(Sys.time()),
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

# -- Delivery: a short, best-effort POST via the `curl` package — the same HTTP
# -- library the rest of the R client uses (see communication.R). Synchronous
# -- like every other h2o REST call, but with tight connect/timeout limits and
# -- wrapped in tryCatch, so an unreachable receiver costs at most ~1-2s and
# -- never raises into the caller.

.h2o.telemetry.post <- function(payload) {
  body <- jsonlite::toJSON(payload, auto_unbox = TRUE, null = "null")
  url  <- .h2o.telemetry.resolve_url()
  tryCatch({
    h <- curl::new_handle()
    curl::handle_setheaders(h, "Content-Type" = "application/json")
    curl::handle_setopt(h, post = TRUE, postfields = body,
                        connecttimeout = 1L, timeout = .h2o.telemetry.timeout_secs)
    curl::curl_fetch_memory(url, handle = h)
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

#' @keywords internal
.h2o.send_init_telemetry <- function(h2o_version, cluster_shape = NULL, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
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

#' @keywords internal
.h2o.send_algo_train <- function(h2o_version, algo, family, outcome,
                                 duration_ms, n_rows, n_cols, n_models = NULL,
                                 attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch({
    base <- c(
      list(event = "algo_train"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        algo               = algo,
        family             = if (is.null(family) || is.na(family)) NULL else family,
        outcome            = outcome,
        duration_ms_bucket = bucketize_duration_ms(duration_ms),
        rows_bucket        = bucketize_rows(n_rows),
        cols_bucket        = bucketize_cols(n_cols),
        n_models           = if (is.null(n_models) || is.na(n_models)) NULL else as.integer(n_models)
      )
    )
    .h2o.telemetry.with_extras(base, attributes = attributes)
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_algo_score <- function(h2o_version, algo, family, outcome,
                                 duration_ms, n_rows, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch({
    base <- c(
      list(event = "algo_score"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        algo               = algo,
        family             = if (is.null(family) || is.na(family)) NULL else family,
        outcome            = outcome,
        rows_bucket        = bucketize_rows(n_rows),
        duration_ms_bucket = bucketize_duration_ms(duration_ms)
      )
    )
    .h2o.telemetry.with_extras(base, attributes = attributes)
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_mojo_download <- function(h2o_version, algo, family, outcome,
                                    compressed_size_bytes, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch({
    base <- c(
      list(event = "mojo_download"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        algo             = algo,
        family           = if (is.null(family) || is.na(family)) NULL else family,
        outcome          = outcome,
        mojo_size_bucket = bucketize_mojo_size(compressed_size_bytes)
      )
    )
    .h2o.telemetry.with_extras(base, attributes = attributes)
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_upload <- function(h2o_version, file_format, compressed_size_bytes, outcome,
                             frame_shape = NULL, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch({
    base <- c(
      list(event = "upload"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        file_format      = file_format,
        # data_size_bucket is REQUIRED on upload (client always knows the size).
        data_size_bucket = bucketize_data_size(compressed_size_bytes),
        outcome          = outcome
      )
    )
    .h2o.telemetry.with_extras(base,
      extras     = .h2o.telemetry.strip_null(frame_shape %||% list()),
      attributes = attributes)
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_import <- function(h2o_version, source_scheme, file_format, outcome,
                             compressed_size_bytes = NULL,
                             frame_shape = NULL, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch({
    base <- c(
      list(event = "import"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        source_scheme = source_scheme,
        file_format   = file_format,
        outcome       = outcome
      )
    )
    extras <- .h2o.telemetry.strip_null(c(
      # data_size_bucket is nullable on import (remote sources may not expose
      # the size cheaply) — omit when unknown.
      if (!is.null(compressed_size_bytes))
        list(data_size_bucket = bucketize_data_size(compressed_size_bytes))
      else NULL,
      frame_shape %||% list()
    ))
    .h2o.telemetry.with_extras(base, extras = extras, attributes = attributes)
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_automl_run <- function(h2o_version, algo, family, outcome,
                                 max_models = NULL, max_runtime_secs = NULL,
                                 sort_metric = NULL, leaderboard_size = NULL,
                                 attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch({
    base <- c(
      list(event = "automl_run"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        algo                     = algo,
        family                   = if (is.null(family) || is.na(family)) NULL else family,
        outcome                  = outcome,
        max_models_bucket        = if (!is.null(max_models))       bucketize_max_models(as.integer(max_models)) else NULL,
        max_runtime_secs_bucket  = if (!is.null(max_runtime_secs)) bucketize_max_runtime_secs(as.numeric(max_runtime_secs)) else NULL,
        sort_metric              = .h2o.telemetry.normalize_sort_metric(sort_metric),
        leaderboard_size_bucket  = if (!is.null(leaderboard_size)) bucketize_leaderboard_size(as.integer(leaderboard_size)) else NULL
      )
    )
    .h2o.telemetry.with_extras(.h2o.telemetry.strip_null(base), attributes = attributes)
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_model_save <- function(h2o_version, algo, family, outcome, fmt,
                                 compressed_size_bytes = NULL, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch({
    base <- c(
      list(event = "model_save"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        algo                 = algo,
        family               = if (is.null(family) || is.na(family)) NULL else family,
        outcome              = outcome,
        format               = fmt,
        artifact_size_bucket = if (!is.null(compressed_size_bytes)) bucketize_artifact_size(compressed_size_bytes) else NULL
      )
    )
    .h2o.telemetry.with_extras(.h2o.telemetry.strip_null(base), attributes = attributes)
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_model_load <- function(h2o_version, algo, family, outcome, fmt,
                                 compressed_size_bytes = NULL, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch({
    base <- c(
      list(event = "model_load"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        algo                 = algo,
        family               = if (is.null(family) || is.na(family)) NULL else family,
        outcome              = outcome,
        format               = fmt,
        artifact_size_bucket = if (!is.null(compressed_size_bytes)) bucketize_artifact_size(compressed_size_bytes) else NULL
      )
    )
    .h2o.telemetry.with_extras(.h2o.telemetry.strip_null(base), attributes = attributes)
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_model_download <- function(h2o_version, algo, family, outcome, fmt,
                                     compressed_size_bytes = NULL, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch({
    base <- c(
      list(event = "model_download"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        algo                 = algo,
        family               = if (is.null(family) || is.na(family)) NULL else family,
        outcome              = outcome,
        format               = fmt,  # "binary" or "pojo" — never "mojo"
        artifact_size_bucket = if (!is.null(compressed_size_bytes)) bucketize_artifact_size(compressed_size_bytes) else NULL
      )
    )
    .h2o.telemetry.with_extras(.h2o.telemetry.strip_null(base), attributes = attributes)
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_frame_parsed <- function(h2o_version, file_format, outcome, duration_ms,
                                   n_rows, n_cols, frame_memory_gb = NULL, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch({
    # rows / cols / duration are REQUIRED on frame_parsed — never stripped.
    base <- c(
      list(event = "frame_parsed"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        file_format        = file_format,
        outcome            = outcome,
        rows_bucket        = bucketize_rows(n_rows),
        cols_bucket        = bucketize_cols(n_cols),
        duration_ms_bucket = bucketize_duration_ms(duration_ms)
      )
    )
    if (!is.null(frame_memory_gb)) {
      base$frame_memory_gb_bucket <- bucketize_frame_memory_gb(frame_memory_gb)
    }
    .h2o.telemetry.with_extras(base, attributes = attributes)
  }, error = function(e) NULL)
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.r_version_safe <- function() {
  tryCatch(as.character(utils::packageVersion("h2o")),
           error = function(e) "")
}

# -- Derivation helpers for call-site wiring --

.h2o.telemetry.source_scheme_map <- list(
  s3 = "s3", s3a = "s3", s3n = "s3",
  hdfs = "hdfs",
  gs = "gcs", gcs = "gcs",
  http = "http", https = "http",
  file = "local"
)

#' Map a URL/path to one of: s3 / hdfs / gcs / http / local / other.
#' @keywords internal
.h2o.derive_source_scheme <- function(path) {
  if (is.null(path) || !nzchar(path)) return("local")
  s <- tolower(trimws(as.character(path)))
  if (grepl("://", s, fixed = TRUE)) {
    scheme <- sub("://.*$", "", s)
    mapped <- .h2o.telemetry.source_scheme_map[[scheme]]
    if (is.null(mapped)) return("other")
    return(mapped)
  }
  "local"
}

.h2o.telemetry.file_format_table <- list(
  c(".parquet", "parquet"),
  c(".orc",     "orc"),
  c(".arff",    "arff"),
  c(".csv",     "csv"),
  c(".tsv",     "csv"),
  c(".gz",      "other"),
  c(".zip",     "other")
)

#' Map a filename/extension to one of: csv / parquet / orc / arff / other.
#' @keywords internal
.h2o.derive_file_format <- function(path_or_name) {
  if (is.null(path_or_name) || !nzchar(path_or_name)) return("other")
  s <- tolower(trimws(as.character(path_or_name)))
  for (row in .h2o.telemetry.file_format_table) {
    if (endsWith(s, row[[1]])) return(row[[2]])
  }
  "other"
}

#' Return bucket fields for a parsed H2OFrame, or empty list on failure.
#' @keywords internal
.h2o.derive_frame_shape <- function(frame) {
  if (is.null(frame)) return(list())
  out <- list()
  nr <- tryCatch(as.numeric(nrow(frame)), error = function(e) NA_real_)  # numeric: rows can exceed 2^31
  nc <- tryCatch(as.numeric(ncol(frame)), error = function(e) NA_real_)
  if (!is.na(nr) && nr >= 0L) out$rows_bucket <- bucketize_rows(nr)
  if (!is.na(nc) && nc >= 0L) out$cols_bucket <- bucketize_cols(nc)
  # frame_memory_gb_bucket is intentionally omitted: the R client does not expose
  # a frame's in-memory byte size, and this field is nullable on the wire.
  out
}

#' Return list(n_rows, n_cols, frame_memory_gb) for a parsed H2OFrame, used by
#' the frame_parsed call sites (which need raw counts and REQUIRE rows + cols).
#' Returns NULL when rows/cols can't be determined so the caller skips the event.
#' @keywords internal
.h2o.derive_frame_dims <- function(frame) {
  if (is.null(frame)) return(NULL)
  nr <- tryCatch(as.numeric(nrow(frame)), error = function(e) NA_real_)  # numeric: rows can exceed 2^31
  nc <- tryCatch(as.numeric(ncol(frame)), error = function(e) NA_real_)
  if (is.na(nr) || is.na(nc)) return(NULL)
  # frame_memory_gb is left NULL: the R client does not expose a frame's
  # in-memory byte size, and this field is nullable on the wire.
  list(n_rows = nr, n_cols = nc, frame_memory_gb = NULL)
}
