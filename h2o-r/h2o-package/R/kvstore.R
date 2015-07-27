#-----------------------------------------------------------------------------------------------------------------------
# H2O Key-Value Store Functions
#-----------------------------------------------------------------------------------------------------------------------

.key.validate <- function(key) {
  if (!missing(key) && !is.null(key)) {
    stopifnot( is.character(key) && length(key) == 1L && !is.na(key), "`key` must be a character string")
    if( nzchar(key) && regexpr("^[a-zA-Z_][a-zA-Z0-9_.]*$", key)[1L] == -1L )
      stop("`key` must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$'")
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
  sprintf("%s_%d", prefix, conn@mutable$key_count)  # removed session_id
}


#` Fetch the first N rows on demand, caching them in x$data; also cache x$nrow
.fetch.data <- function(x,N) {
  stopifnot(is.Frame(x))
  stopifnot(!missing(N))
  .eval.frame(x)
  if( is.null(x$data) || nrow(x$data) < N ) {
    # TODO: extract N rows instead of 100
    res <- .h2o.__remoteSend(paste0(.h2o.__FRAMES, "/", .id(x)))$frames[[1]]
    data    <- as.data.frame(lapply(res$columns, function(c) c$data ))
    colnames(data) <- unlist(lapply(res$columns, function(c) c$label))
    for( i in 1:length(data) ) {  # Set factor levels
      dom <- res$columns[[i]]$domain
      if( !is.null(dom) )
        data[,i] <- factor(data[,i],levels=seq(0,length(dom)-1),labels=dom)
    }
    x$data <- data
    x$nrow <- res$rows
  }
  x$data
}

