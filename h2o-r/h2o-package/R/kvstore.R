#-----------------------------------------------------------------------------------------------------------------------
# H2O Key-Value Store Functions
#-----------------------------------------------------------------------------------------------------------------------

.key.validate <- function(key) {
  if (!missing(key) && !is.null(key)) {
    if (!is.character(key) || length(key) != 1L || is.na(key)) {
      stop("`key` must be a character string")
    }
    if (nzchar(key)) {
      if (regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1L] == -1L)
        stop("`key` must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
      .h2o.gc() # clean up KV store
    }
  }
  invisible(TRUE)
}

.key.make <- function(prefix = "rapids") {
  conn <- h2o.getConnection()
  if (conn@mutable$key_count == .Machine$integer.max) {
    conn@mutable$session_id <- .init.session_id()
    conn@mutable$key_count  <- 0L
  }
  conn@mutable$key_count <- conn@mutable$key_count + 1L
  key <- sprintf("%s_%d", prefix, conn@mutable$key_count)  # removed session_id
  key
}

