#'
#' Telemetry for h2o-r (v1.1).
#'
#' Sends fire-and-forget HTTPS POSTs to the telemetry receiver describing
#' client activity: one `init` event at `h2o.init()` plus five activity
#' event types as the user trains, scores, downloads MOJOs, uploads, and
#' imports frames. All requests are synchronous but capped at ~2s by
#' libcurl's connecttimeout/timeout, and fully wrapped in tryCatch, so
#' the worst case is a 2s pause on a fully offline machine. We
#' intentionally do not use `parallel::mcparallel` — forking after
#' libcurl/Security framework initialization segfaults on macOS.
#'
#' Honors two opt-out environment variables (first match wins):
#'     H2O_DISABLE_TELEMETRY -- H2O-specific kill switch
#'     DO_NOT_TRACK          -- industry-standard opt-out
#'
#' The receiver URL can be overridden via the `H2O_TELEMETRY_URL`
#' environment variable. Default points at the local-dev receiver
#' (127.0.0.1:8000); the production cloud cutover will set the env var
#' to the production URL.
#'
#' See `.planning/h2o-3-client-integration.md` for the wire contract.
#'

# v1.1 default points at local-dev receiver; production cloud cutover
# will set H2O_TELEMETRY_URL via the install scripts.
.h2o.telemetry.url <- "http://127.0.0.1:8000/v1/event"
.h2o.telemetry.payload_version <- 1L
.h2o.telemetry.timeout_secs <- 2L

# Per-process shared session_id, minted on first .h2o.send_init_telemetry()
# and reused for every activity event. Lives in a private env so it
# persists across function calls without being a package-global variable.
.h2o.telemetry.state <- new.env(parent = emptyenv())
.h2o.telemetry.state$session_id <- NULL

.h2o.telemetry.resolve_url <- function() {
  envv <- Sys.getenv("H2O_TELEMETRY_URL")
  if (nzchar(envv)) return(envv)
  .h2o.telemetry.url
}

.h2o.telemetry.disabled <- function() {
  nzchar(Sys.getenv("H2O_DISABLE_TELEMETRY")) ||
    nzchar(Sys.getenv("DO_NOT_TRACK"))
}

# Generate a random UUIDv4 from 16 bytes — avoids depending on the uuid package.
.h2o.telemetry.uuid <- function() {
  b <- as.integer(sample.int(256L, 16L, replace = TRUE) - 1L)
  b[7]  <- bitwOr(bitwAnd(b[7],  0x0F), 0x40)  # version 4
  b[9]  <- bitwOr(bitwAnd(b[9],  0x3F), 0x80)  # RFC 4122 variant
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
  if (is.null(sid)) {
    sid <- .h2o.telemetry.new_session_id()
  }
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

# -- Bucketize helpers -- copy verbatim from the v1.1 contract.
# DO NOT rename labels or change boundaries; they must be byte-identical
# across Python / R / Java per the wire contract.

bucketize_duration_ms <- function(ms) {
  if (ms < 1000)          return("<1s")
  if (ms < 10000)         return("1s-10s")
  if (ms < 60000)         return("10s-60s")
  if (ms < 600000)        return("1m-10m")
  if (ms < 3600000)       return("10m-1h")
  return(">1h")
}

bucketize_rows <- function(n) {
  if (n < 1000)           return("<1k")
  if (n < 10000)          return("1k-10k")
  if (n < 100000)         return("10k-100k")
  if (n < 1000000)        return("100k-1M")
  if (n < 10000000)       return("1M-10M")
  return(">10M")
}

bucketize_cols <- function(n) {
  if (n < 10)             return("<10")
  if (n < 100)            return("10-100")
  if (n < 1000)           return("100-1k")
  if (n < 10000)          return("1k-10k")
  return(">10k")
}

bucketize_size_bytes <- function(b) {
  if (b < 1048576)        return("<1MB")
  if (b < 10485760)       return("1MB-10MB")
  if (b < 104857600)      return("10MB-100MB")
  if (b < 1073741824)     return("100MB-1GB")
  return(">1GB")
}

# -- Common envelope shared by all events --

.h2o.telemetry.envelope <- function(h2o_version) {
  list(
    payload_version = .h2o.telemetry.payload_version,
    client          = "r",
    h2o_version     = .h2o.telemetry.str(h2o_version),
    os              = .h2o.telemetry.os(),
    os_version      = .h2o.telemetry.str(Sys.info()[["release"]]),
    jvm_version     = "",
    session_id      = .h2o.telemetry.current_session_id(),
    ts              = as.integer(Sys.time())
  )
}

.h2o.telemetry.post <- function(payload) {
  body <- jsonlite::toJSON(payload, auto_unbox = TRUE, null = "null")
  tryCatch({
    h <- RCurl::basicHeaderGatherer()
    t <- RCurl::basicTextGatherer()
    RCurl::curlPerform(
      url            = .h2o.telemetry.resolve_url(),
      postfields     = body,
      writefunction  = t$update,
      headerfunction = h$update,
      httpheader     = c("Content-Type" = "application/json"),
      customrequest  = "POST",
      connecttimeout = 1L,
      timeout        = .h2o.telemetry.timeout_secs,
      verbose        = FALSE
    )
  }, error = function(e) invisible(NULL))
  invisible(NULL)
}

.h2o.telemetry.send <- function(payload) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  tryCatch(.h2o.telemetry.post(payload), error = function(e) invisible(NULL))
  invisible(NULL)
}

# -- Public emitters -- one per event type. All return invisible(NULL) and
# never raise; suitable for plain unguarded calls from hot paths.

#' Send one `event=init` telemetry POST. Mints a fresh session_id.
#' @keywords internal
.h2o.send_init_telemetry <- function(h2o_version) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  .h2o.telemetry.new_session_id()
  payload <- tryCatch(
    c(list(event = "init"), .h2o.telemetry.envelope(h2o_version)),
    error = function(e) NULL
  )
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_algo_train <- function(h2o_version, algo, family, outcome,
                                 duration_ms, n_rows, n_cols, n_models = NULL) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch(
    c(
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
    ),
    error = function(e) NULL
  )
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_algo_score <- function(h2o_version, algo, family, outcome,
                                 duration_ms, n_rows) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch(
    c(
      list(event = "algo_score"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        algo               = algo,
        family             = if (is.null(family) || is.na(family)) NULL else family,
        outcome            = outcome,
        rows_bucket        = bucketize_rows(n_rows),
        duration_ms_bucket = bucketize_duration_ms(duration_ms)
      )
    ),
    error = function(e) NULL
  )
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_mojo_download <- function(h2o_version, algo, family, outcome,
                                    compressed_size_bytes) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch(
    c(
      list(event = "mojo_download"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        algo                   = algo,
        family                 = if (is.null(family) || is.na(family)) NULL else family,
        outcome                = outcome,
        compressed_size_bucket = bucketize_size_bytes(compressed_size_bytes)
      )
    ),
    error = function(e) NULL
  )
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_upload <- function(h2o_version, file_format, compressed_size_bytes, outcome) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch(
    c(
      list(event = "upload"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        file_format            = file_format,
        compressed_size_bucket = bucketize_size_bytes(compressed_size_bytes),
        outcome                = outcome
      )
    ),
    error = function(e) NULL
  )
  if (is.null(payload)) return(invisible(NULL))
  .h2o.telemetry.send(payload)
}

#' @keywords internal
.h2o.send_import <- function(h2o_version, source_scheme, file_format, outcome) {
  if (.h2o.telemetry.disabled()) return(invisible(NULL))
  payload <- tryCatch(
    c(
      list(event = "import"),
      .h2o.telemetry.envelope(h2o_version),
      list(
        source_scheme = source_scheme,
        file_format   = file_format,
        outcome       = outcome
      )
    ),
    error = function(e) NULL
  )
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

# Order matters: longer / more-specific extensions first.
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
