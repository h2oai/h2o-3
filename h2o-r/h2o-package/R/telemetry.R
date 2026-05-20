#'
#' Telemetry for h2o-r (v1.5).
#'
#' Sends fire-and-forget HTTPS POSTs to the telemetry receiver describing
#' client activity. Primary delivery path is system `curl` invoked with
#' `wait = FALSE` — the child process runs detached, R returns
#' immediately, and an unreachable server is invisible to the caller.
#' Fallback path (when system `curl` is missing): synchronous `RCurl`
#' with tight `connecttimeout`/`timeout` limits, fully wrapped in
#' tryCatch.
#'
#' Honors two opt-out environment variables (first match wins):
#'     H2O_DISABLE_TELEMETRY -- H2O-specific kill switch
#'     DO_NOT_TRACK          -- industry-standard opt-out
#'
#' URL override: H2O_TELEMETRY_URL.
#'
#' See `.planning/h2o-3-client-integration.md` and
#' `.planning/h2o-3-update-v1.3-v1.4.md` for the wire contract.

# v2.0 production endpoint. Override per-deployment via H2O_TELEMETRY_URL
# (internal / private receivers, local dev pointing at 127.0.0.1:8000, etc.).
.h2o.telemetry.url <- "https://telemetry.h2o.ai/v1/event"
.h2o.telemetry.payload_version <- 1L
.h2o.telemetry.timeout_secs <- 2L
.h2o.telemetry.max_version_len <- 64L

# Per-process shared session_id + caches in a private env so they persist
# across function calls without leaking package globals.
.h2o.telemetry.state <- new.env(parent = emptyenv())
.h2o.telemetry.state$session_id        <- NULL
.h2o.telemetry.state$curl_path         <- NULL  # cached lookup of system curl
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
.h2o.telemetry.uuid <- function() {
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

# -- Bucketize helpers — byte-identical labels across Python / R / Java --

bucketize_duration_ms <- function(ms) {
  if (ms < 1000)          return("<1s")
  if (ms < 10000)         return("1s-10s")
  if (ms < 60000)         return("10s-60s")
  if (ms < 600000)        return("1m-10m")
  if (ms < 3600000)       return("10m-1h")
  ">1h"
}

bucketize_rows <- function(n) {
  if (n < 1000)           return("<1k")
  if (n < 10000)          return("1k-10k")
  if (n < 100000)         return("10k-100k")
  if (n < 1000000)        return("100k-1M")
  if (n < 10000000)       return("1M-10M")
  ">10M"
}

bucketize_cols <- function(n) {
  if (n < 10)             return("<10")
  if (n < 100)            return("10-100")
  if (n < 1000)           return("100-1k")
  if (n < 10000)          return("1k-10k")
  ">10k"
}

bucketize_size_bytes <- function(b) {
  # Decimal MB/GB per v1.5 contract — labels are "MB" / "GB", not "MiB".
  if (b < 1000000)        return("<1MB")
  if (b < 10000000)       return("1MB-10MB")
  if (b < 100000000)      return("10MB-100MB")
  if (b < 1000000000)     return("100MB-1GB")
  ">1GB"
}

# -- v1.4 / v1.5 bucket helpers --

bucketize_cluster_nodes <- function(n) {
  if (n == 1)  return("1")
  if (n <= 4)  return("2-4")
  if (n <= 16) return("5-16")
  if (n <= 64) return("17-64")
  ">64"
}

bucketize_cluster_memory_gb <- function(gb) {
  if (gb < 1)   return("<1")
  if (gb < 8)   return("1-8")
  if (gb < 32)  return("8-32")
  if (gb < 128) return("32-128")
  if (gb < 512) return("128-512")
  ">512"
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

# -- Runtime version detection --

.h2o.telemetry.r_version <- function() {
  tryCatch(
    .h2o.telemetry.cap_version(
      paste(R.version$major, R.version$minor, sep = ".")
    ),
    error = function(e) NULL
  )
}

.h2o.telemetry.detect_java <- function() {
  if (!is.null(.h2o.telemetry.state$java_info)) {
    return(.h2o.telemetry.state$java_info)
  }
  info <- list(version = NULL, vendor = NULL)
  tryCatch({
    out <- suppressWarnings(system2(
      "java",
      args = c("-XshowSettings:properties", "-version"),
      stdout = TRUE, stderr = TRUE
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
    info <- tryCatch(h2o.clusterStatus(), error = function(e) NULL)
    if (is.null(info)) info <- tryCatch(h2o.clusterInfo(), error = function(e) NULL)
    if (is.null(info)) return(out)
    cloud_size <- tryCatch(as.integer(info$cloud_size %||% NA), error = function(e) NA_integer_)
    if (length(cloud_size) > 1L) cloud_size <- cloud_size[[1L]]
    if (!is.na(cloud_size) && cloud_size > 0L) {
      out$cluster_nodes_bucket <- bucketize_cluster_nodes(cloud_size)
    }
    # cluster total memory: prefer max_mem if exposed, else free_mem
    max_mem <- info$max_mem %||% info$free_mem %||% NULL
    if (!is.null(max_mem)) {
      total <- tryCatch(sum(as.numeric(max_mem), na.rm = TRUE), error = function(e) 0)
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
    ts              = as.integer(Sys.time())
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

# -- Delivery: prefer detached system curl; fall back to synchronous RCurl --

.h2o.telemetry.find_curl <- function() {
  cp <- .h2o.telemetry.state$curl_path
  if (!is.null(cp)) return(cp)
  cp <- tryCatch(Sys.which("curl"), error = function(e) "")
  if (length(cp) == 0L || is.na(cp[[1]]) || !nzchar(cp[[1]])) {
    cp <- ""
  } else {
    cp <- as.character(cp[[1]])
  }
  .h2o.telemetry.state$curl_path <- cp
  cp
}

.h2o.telemetry.post_async_curl <- function(body, url) {
  # Write body to a tempfile (curl's --data-binary @file is the safest way
  # to ship arbitrary JSON without shell escaping). The tempfile is
  # intentionally leaked — curl reads it after R returns, and tempdir() is
  # OS-cleaned on session exit.
  tf <- tempfile(pattern = "h2o-tel-", fileext = ".json")
  writeLines(body, tf, useBytes = TRUE)
  curl_bin <- .h2o.telemetry.find_curl()
  tryCatch({
    # DNT: 0 explicitly signals "user has not opted out via the W3C DNT
    # mechanism" — defends against transparent proxies that inject DNT: 1
    # on outbound traffic and would otherwise silently kill telemetry.
    # Users opt out via env vars, never by us sending DNT: 1.
    system2(
      curl_bin,
      args = c("-s", "-o", if (.Platform$OS.type == "windows") "NUL" else "/dev/null",
               "-m", as.character(.h2o.telemetry.timeout_secs),
               "--connect-timeout", "1",
               "-H", "Content-Type: application/json",
               "-H", "DNT: 0",
               "--data-binary", paste0("@", tf),
               "-X", "POST",
               url),
      wait = FALSE, stdout = FALSE, stderr = FALSE
    )
  }, error = function(e) invisible(NULL))
  invisible(NULL)
}

.h2o.telemetry.post_sync_rcurl <- function(body, url) {
  tryCatch({
    h <- RCurl::basicHeaderGatherer()
    t <- RCurl::basicTextGatherer()
    RCurl::curlPerform(
      url            = url,
      postfields     = body,
      writefunction  = t$update,
      headerfunction = h$update,
      httpheader     = c("Content-Type" = "application/json", "DNT" = "0"),
      customrequest  = "POST",
      connecttimeout = 1L,
      timeout        = .h2o.telemetry.timeout_secs,
      verbose        = FALSE
    )
  }, error = function(e) invisible(NULL))
  invisible(NULL)
}

.h2o.telemetry.post <- function(payload) {
  body <- jsonlite::toJSON(payload, auto_unbox = TRUE, null = "null")
  url  <- .h2o.telemetry.resolve_url()
  if (nzchar(.h2o.telemetry.find_curl())) {
    .h2o.telemetry.post_async_curl(body, url)
  } else {
    .h2o.telemetry.post_sync_rcurl(body, url)
  }
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
    java_vendor    = .h2o.telemetry.java_vendor()
  ))
}

#' @keywords internal
.h2o.send_init_telemetry <- function(h2o_version, cluster_shape = NULL, attributes = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  .h2o.telemetry.new_session_id()
  payload <- tryCatch({
    base <- c(list(event = "init"), .h2o.telemetry.envelope(h2o_version))
    extras <- c(.h2o.telemetry.runtime_fields(), .h2o.telemetry.strip_null(cluster_shape %||% list()))
    .h2o.telemetry.with_extras(base, extras = extras, attributes = attributes)
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
    .h2o.telemetry.with_extras(base, extras = extras, attributes = attributes)
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
        algo                   = algo,
        family                 = if (is.null(family) || is.na(family)) NULL else family,
        outcome                = outcome,
        compressed_size_bucket = bucketize_size_bytes(compressed_size_bytes)
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
        file_format            = file_format,
        compressed_size_bucket = bucketize_size_bytes(compressed_size_bytes),
        outcome                = outcome
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
      if (!is.null(compressed_size_bytes))
        list(compressed_size_bucket = bucketize_size_bytes(compressed_size_bytes))
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
        sort_metric              = if (is.null(sort_metric) || is.na(sort_metric)) NULL else as.character(sort_metric),
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
        algo                   = algo,
        family                 = if (is.null(family) || is.na(family)) NULL else family,
        outcome                = outcome,
        format                 = fmt,
        compressed_size_bucket = if (!is.null(compressed_size_bytes)) bucketize_size_bytes(compressed_size_bytes) else NULL
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
        algo                   = algo,
        family                 = if (is.null(family) || is.na(family)) NULL else family,
        outcome                = outcome,
        format                 = fmt,
        compressed_size_bucket = if (!is.null(compressed_size_bytes)) bucketize_size_bytes(compressed_size_bytes) else NULL
      )
    )
    .h2o.telemetry.with_extras(.h2o.telemetry.strip_null(base), attributes = attributes)
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

#' Return v1.5 bucket fields for a parsed H2OFrame, or empty list on failure.
#' @keywords internal
.h2o.derive_frame_shape <- function(frame) {
  if (is.null(frame)) return(list())
  out <- list()
  nr <- tryCatch(as.integer(nrow(frame)), error = function(e) NA_integer_)
  nc <- tryCatch(as.integer(ncol(frame)), error = function(e) NA_integer_)
  if (!is.na(nr) && nr >= 0L) out$rows_bucket <- bucketize_rows(nr)
  if (!is.na(nc) && nc >= 0L) out$cols_bucket <- bucketize_cols(nc)
  # frame.byte_size equivalent in R: use h2o.getFrame()'s byte_size if accessible.
  bs <- tryCatch(attr(frame, "byte_size"), error = function(e) NULL)
  if (!is.null(bs) && is.numeric(bs) && bs > 0) {
    out$frame_memory_gb_bucket <- bucketize_frame_memory_gb(bs / (1024^3))
  }
  out
}
