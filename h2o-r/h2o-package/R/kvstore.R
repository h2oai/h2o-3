#-----------------------------------------------------------------------------------------------------------------------
# H2O Key-Value Store Functions
#-----------------------------------------------------------------------------------------------------------------------

.key.validate <- function(key) {
  if (!missing(key) && !is.null(key)) {
    stopifnot( is.character(key) && length(key) == 1L && !is.na(key) )
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
  stopifnot(is(x, "Frame"))
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

#` Flush any cached data
.flush.data <- function(x) {
  rm("data",envir=x);
  rm("nrow",envir=x);
}

#
#' Delete Objects In H2O
#'
#' Remove the h2o Big Data object(s) having the key name(s) from ids.
#'
#' @param ids The hex key associated with the object to be removed.
#' @seealso \code{\link{h2o.assign}}, \code{\link{h2o.ls}}
#' @export
h2o.rm <- function(ids) {
  if( is(ids, "Frame") ) {
    if( !is.null(ids$refcnt) ) stop("Trying to remove a client-managed temp; try assigning NULL over the variable instead")
    ids <- .id(ids); 
  }
  if(!is.character(ids)) stop("`ids` must be of class character")

  for(i in seq_len(length(ids)))
    .h2o.__remoteSend(paste0(.h2o.__DKV, "/", ids[[i]]), method = "DELETE")
}


#'
#' List Keys on an H2O Cluster
#'
#' Accesses a list of object keys in the running instance of H2O.
#'
#' @return Returns a list of hex keys in the current H2O instance.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' h2o.ls(localH2O)
#' @export
h2o.ls <- function() {
  .h2o.gc()
  .eval.frame(.newExpr("ls"))
}