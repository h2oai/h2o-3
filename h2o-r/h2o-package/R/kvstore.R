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


#'
#' List Keys on an H2O Cluster
#'
#' Accesses a list of object keys in the running instance of H2O.
#'
#' @return Returns a list of hex keys in the current H2O instance.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' h2o.ls()
#' }
#' @export
h2o.ls <- function() {
  .h2o.gc()
  .fetch.data(.newExpr("ls"),10000L)
}

#'
#' Remove All Objects on the H2O Cluster
#'
#' Removes the data from the h2o cluster, but does not remove the local references.
#'
#' @param timeout_secs Timeout in seconds. Default is no timeout.
#' @seealso \code{\link{h2o.rm}}
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' h2o.ls()
#' h2o.removeAll()
#' h2o.ls()
#' }
#' @export
h2o.removeAll <- function(timeout_secs=0) {
  tryCatch(
    invisible(.h2o.__remoteSend(.h2o.__DKV, method = "DELETE", timeout=timeout_secs)),
    error = function(e) {
      print("Timeout on DELETE /DKV from R")
      print("Attempt thread dump...")
      h2o.killMinus3()
      stop(e)
    })
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
  if( is.Frame(ids) ) {
    if( is.null( attr(ids, "id")) ) stop("Trying to remove a client-managed temp; try assigning NULL over the variable instead")
    ids <- attr(ids, "id")
  }
  if(!is.character(ids)) stop("`ids` must be of class character")

  for(i in seq_len(length(ids)))
    .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=paste0("(rm ",ids[[i]],")"), method = "POST")
}

#'
#' Get an R Reference to an H2O Dataset, that will NOT be GC'd by default
#'
#' Get the reference to a frame with the given id in the H2O instance.
#'
#' @param id A string indicating the unique frame of the dataset to retrieve.
#' @export
h2o.getFrame <- function(id) {
  fr <- .newFrame(id,id,-1,-1)
  .fetch.data(fr,1L)
  fr
}

#' Get an R reference to an H2O model
#'
#' Returns a reference to an existing model in the H2O instance.
#'
#' @param model_id A string indicating the unique model_id of the model to retrieve.
#' @return Returns an object that is a subclass of \linkS4class{H2OModel}.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' iris.hex <- as.h2o(iris, "iris.hex")
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


  parameters$x <- json$output$names
  allparams$x  <- json$output$names
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

#'
#' Download the Scoring POJO (Plain Old Java Object) of a H2O Model
#'
#' @param model An H2OModel
#' @param path The path to the directory to store the POJO (no trailing slash). If "", then print to
#'             to console. The file name will be a compilable java file name.
#' @param getjar Whether to also download the h2o-genmodel.jar file needed to compile the POJO 
#' @return If path is "", then pretty print the POJO to the console.
#'         Otherwise save it to the specified directory.
#' @examples
#' \donttest{
#' library(h2o)
#' h <- h2o.init(nthreads=-1)
#' fr <- as.h2o(iris)
#' my_model <- h2o.gbm(x=1:4, y=5, training_frame=fr)
#'
#' h2o.download_pojo(my_model)  # print the model to screen
#' # h2o.download_pojo(my_model, getwd())  # save the POJO and jar file to the current working 
#' #                                         directory, NOT RUN
#' # h2o.download_pojo(my_model, getwd(), getjar = FALSE )  # save only the POJO to the current
#' #                                                           working directory, NOT RUN
#' h2o.download_pojo(my_model, getwd())  # save to the current working directory
#' }
#' @export
h2o.download_pojo <- function(model, path="", getjar=TRUE) {
  model_id <- model@model_id
  java <- .h2o.__remoteSend(method = "GET", paste0(.h2o.__MODELS, ".java/", model_id), raw=TRUE)
  file.path <- paste0(path, "/", model_id, ".java")
  if( path == "" ) cat(java)
  else {
    write(java, file=file.path)
    if (getjar) {
      .__curlError = FALSE
      .__curlErrorMessage = ""
      url = .h2o.calcBaseURL(h2oRestApiVersion = .h2o.__REST_API_VERSION, urlSuffix = "h2o-genmodel.jar")
      tmp = tryCatch(getBinaryURL(url = url,
                          useragent = R.version.string),
                   error = function(x) { .__curlError <<- TRUE; .__curlErrorMessage <<- x$message })
      if (! .__curlError) {
        jar.path <- paste0(path, "/h2o-genmodel.jar")
        writeBin(tmp, jar.path, useBytes = TRUE)
      }
    }
  }

  if( path!="") print( paste0("POJO written to: ", file.path) )
}
