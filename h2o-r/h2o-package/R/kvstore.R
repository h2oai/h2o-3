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
  stopifnot(!missing(N))
  .eval.frame(chk.Frame(x))
  if( is.null(x:data) || (is.data.frame(x:data) && nrow(x:data) < N) ) {
    res <- .h2o.__remoteSend(paste0(.h2o.__FRAMES, "/", x:id, "?row_count=",N))$frames[[1]]
    # Convert to data.frame, handling short data (trailing NAs)
    # Numeric data is OK, but can be short if e.g., there are trailing NAs
    # String data is a list form; convert to a vector (and convert NULL to NA)
    L <- lapply(res$columns, function(c) {
      if( c$type!="string" )  c$data
      else  sapply(c$string_data, function(str) { if(is.null(str)) NA_character_ else str }); 
    })
    # Pad out to same length (square up ragged data), and convert to data.frame
    maxlen <- max(sapply(L,length))
    data <- do.call(data.frame,lapply(L,function(row) c(row,rep(NA,maxlen-length(row)))))
    # Zero rows?  Then force a zero-length full width data.frame
    if( length(data)==0 ) data <- as.data.frame(matrix(NA,ncol=length(res$columns),nrow=0L))
    colnames(data) <- unlist(lapply(res$columns, function(c) c$label))
    if( nrow(data) > 0 ) {
      for( i in 1:length(data) ) {  # Set factor levels
        dom <- res$columns[[i]]$domain
        if( !is.null(dom) ) # H2O has a domain; force R to do so also
          data[,i] <- factor(data[,i],levels=seq(0,length(dom)-1),labels=dom)
        else if( is.factor(data[,i]) ) # R has a domain, but H2O does not
          data[,i] <- as.character(data[,i]) # Force to string type
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
  x
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
  # Eager evaluate, copied from .eval.frame
  exec_str <- .eval.impl(data);
  print(paste0("ASSIGN ",key," = EXPR: ",exec_str))
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
    if( is.null(ids:id) ) stop("Trying to remove a client-managed temp; try assigning NULL over the variable instead")
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

#' Get an R reference to an H2O model
#'
#' Returns a reference to an existing model in the H2O instance.
#'
#' @param model_id A string indicating the unique model_id of the model to retrieve.
#' @return Returns an object that is a subclass of \linkS4class{H2OModel}.
#' @examples
#' \donttest{
#' library(h2o)
#' localH2O <- h2o.init()
#'
#' iris.hex <- as.h2o(iris, localH2O, "iris.hex")
#' model_id <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.hex)@@model_id
#' model.retrieved <- h2o.getModel(model_id)
#' }
#' @export
h2o.getModel <- function(model_id) {
  json <- .h2o.__remoteSend(method = "GET", paste0(.h2o.__MODELS, "/", model_id))$models[[1L]]
  model_category <- json$output$model_category
  if (is.null(model_category))
    model_category <- "Unknown"
  else if (!(model_category %in% c("Unknown", "Binomial", "Multinomial", "Regression", "Clustering", "AutoEncoder", "DimReduction")))
    stop(paste0("model_category, \"", model_category,"\", missing in the output"))
  Class <- paste0("H2O", model_category, "Model")
  model <- json$output[!(names(json$output) %in% c("__meta", "names", "domains", "model_category"))]
  MetricsClass <- paste0("H2O", model_category, "Metrics")
  # setup the metrics objects inside of model...
  model$training_metrics   <- new(MetricsClass, algorithm=json$algo, on_train=TRUE, on_valid=FALSE, on_xval=FALSE, metrics=model$training_metrics)
  model$validation_metrics <- new(MetricsClass, algorithm=json$algo, on_train=FALSE, on_valid=TRUE, on_xval=FALSE, metrics=model$validation_metrics)
  model$cross_validation_metrics <- new(MetricsClass, algorithm=json$algo, on_train=FALSE, on_valid=FALSE, on_xval=TRUE, metrics=model$cross_validation_metrics)
  parameters <- list()
  allparams  <- list()
  lapply(json$parameters, function(param) {
    if (!is.null(param$actual_value)) {
      name <- param$name
      value <- param$actual_value
      mapping <- .type.map[param$type,]
      type    <- mapping[1L, 1L]
      scalar  <- mapping[1L, 2L]

      if (type == "numeric" && value == "Infinity")
        value <- Inf
      else if (type == "numeric" && value == "-Infinity")
        value <- -Inf

      # Parse frame information to a key
      if (type == "Frame")
        value <- value$name
      # Parse model information to a key
      if (type == "H2OModel") {
        value <- value$name
      }

      # Response column needs to be parsed
      if (name == "response_column")
        value <- value$column_name
      allparams[[name]] <<- value
      # Store only user changed parameters into parameters
      # TODO: Should we use !isTrue(all.equal(param$default_value, param$actual_value)) instead?
      if (is.null(param$default_value) || param$required || !identical(param$default_value, param$actual_value))
        parameters[[name]] <<- value
    }
  })

  # Convert ignored_columns/response_column to valid R x/y
  cols <- colnames(h2o.getFrame(parameters$training_frame))

  parameters$x <- setdiff(cols, parameters$ignored_columns)
  allparams$x <- setdiff(cols, allparams$ignored_columns)
  if (!is.null(parameters$response_column))
  {
    parameters$y <- parameters$response_column
    allparams$y <- allparams$response_column
    parameters$x <- setdiff(parameters$x, parameters$y)
    allparams$x <- setdiff(allparams$x, allparams$y)
  }

  allparams$ignored_columns <- NULL
  allparams$response_column <- NULL
  parameters$ignored_columns <- NULL
  parameters$response_column <- NULL
  .newH2OModel(Class         = Class,
               model_id      = model_id,
               algorithm     = json$algo,
               parameters    = parameters,
               allparameters = allparams,
               model         = model)
}

