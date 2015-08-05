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
  stopifnot( is.Frame(x) )
  stopifnot(!missing(N))
  .eval.frame(x)
  if( is.null(x:data) || (is.data.frame(x:data) && nrow(x:data) < N) ) {
    res <- .h2o.__remoteSend(paste0(.h2o.__FRAMES, "/", x:eval, "?row_count=",N))$frames[[1]]
    data    <- as.data.frame(lapply(res$columns, function(c) c$data ))
    if( length(data)==0 ) data <- as.data.frame(matrix(NA,ncol=length(res$columns),nrow=0L))
    colnames(data) <- unlist(lapply(res$columns, function(c) c$label))
    if( length(data) > 0 ) {
      for( i in 1:length(data) ) {  # Set factor levels
        dom <- res$columns[[i]]$domain
        if( !is.null(dom) )
          data[,i] <- factor(data[,i],levels=seq(0,length(dom)-1),labels=dom)
      }
    }
    .set(x,"data",data)
    .set(x,"nrow",res$rows)
  }
  x:data
}

#` Flush any cached data
.flush.data <- function(x) {
  rm("data",envir=x);
  rm("nrow",envir=x);
}

#'
#' Rename an H2O object.
#'
#' Makes a copy of the data frame and gives it the desired the key.
#'
#' @param data An \linkS4class{Frame} object
#' @param key The hex key to be associated with the H2O parsed data object
#'
#' @export
h2o.assign <- function(data, key) {
  .key.validate(key)
  if( !is.null(data:id) && key == data:id ) stop("Destination key must differ from input frame ", key)
  # Eager evalute, copied from .eval.frame
  exec_str <- .eval.impl(data,TRUE);
  res <- .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=exec_str, id=key, method = "POST")
  if( !is.null(res$error) ) stop(paste0("Error From H2O: ", res$error), call.=FALSE)
  .newFrame("h2o.assign",key)
}

#
#' Delete Objects In H2O
#'
#' Remove the h2o Big Data object(s) having the key name(s) from ids.
#'
#' @param ids The hex key associated with the object to be removed.
#' @param pattern A regular expression used to select Frames to remove.
#' @seealso \code{\link{h2o.assign}}, \code{\link{h2o.ls}}
#' @export
h2o.rm <- function(ids,pattern="") {
  if( missing(ids) ) {
    stopifnot(length(pattern) > 1L, is.character(pattern))
    keys <- h2o.ls()[,"key"]
    ids <- keys[grep(pattern, keys)]
  }
  if( is.Frame(ids) ) {
    if( !is.null(ids:id) ) stop("Trying to remove a client-managed temp; try assigning NULL over the variable instead")
    ids <- ids:id;
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
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' h2o.ls()
#' @export
h2o.ls <- function() {
  .h2o.gc()
  .fetch.data(.newExpr("ls"),10L)
}

#'
#' Remove All Objects on the H2O Cluster
#'
#' Removes the data from the h2o cluster, but does not remove the local references.
#'
#' of the H2O server.
#' @seealso \code{\link{h2o.rm}}
#' @examples
#' library(h2o)
#' h2o.init()
#' iris.h2o <- as.h2o(iris)
#' h2o.ls()
#' h2o.removeAll()
#' h2o.ls()
#' @export
h2o.removeAll <- function() invisible(.h2o.__remoteSend(.h2o.__DKV, method = "DELETE"))

#'
#' Get an R Reference to an H2O Dataset, that will NOT be GC'd by default
#'
#' Get the reference to a frame with the given id in the H2O instance.
#'
#' @param id A string indicating the unique frame of the dataset to retrieve.
#' @export
h2o.getFrame <- function(id) .newFrame("getFrame",id)