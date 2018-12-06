#-----------------------------------------------------------------------------------------------------------------------
# H2O Key-Value Store Functions
#-----------------------------------------------------------------------------------------------------------------------

.key.validate <- function(key) {
  if (!missing(key) && !is.null(key)) {
    stopifnot( is.character(key) && length(key) == 1L && !is.na(key) )
    if( nzchar(key) && regexpr("^[a-zA-Z_][a-zA-Z0-9_.-]*$", key)[1L] == -1L )
      stop(paste0("`key` must match the regular expression '^[a-zA-Z_][a-zA-Z0-9_.]*$': ", key))
  }
  invisible(TRUE)
}

.key.make <- function(prefix = "rapids") {
  conn <- h2o.getConnection()
  session_id <- conn@mutable$session_id
  if (conn@mutable$key_count == .Machine$integer.max) {
    session_id <- conn@mutable$session_id <- .init.session_id()
    conn@mutable$key_count  <- 0L
  }
  conn@mutable$key_count <- conn@mutable$key_count + 1L
  sprintf("%s%s_%d", prefix, session_id, conn@mutable$key_count)  # removed session_id
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
  gc()
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
#' @param ids The object or hex key associated with the object to be removed or a vector/list of those things.
#' @seealso \code{\link{h2o.assign}}, \code{\link{h2o.ls}}
#' @export
h2o.rm <- function(ids) {
  gc()
  if( !is.vector(ids) ) x_list = c(ids) else x_list = ids
  for (xi in x_list) {
    if( is.null(xi) ) stop("h2o.rm with NULL object is not supported")
    if( is.H2OFrame(xi) ) {
      xi_id <- attr(xi, "id")       # String or None
      if( is.null(xi_id) ) return() # Lazy frame, never evaluated, nothing in cluster
      .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=paste0("(rm ",xi_id[[1]],")"), session_id=h2o.getConnection()@mutable$session_id, method = "POST")
    } else if( is(xi, "H2OModel") ) {
      .h2o.__remoteSend(paste0(.h2o.__DKV, "/",xi@model_id), method = "DELETE")
    } else if( is.character(xi) ) {
      .h2o.__remoteSend(paste0(.h2o.__DKV, "/",xi), method = "DELETE")
    } else {
      stop("input to h2o.rm must be one of: H2OFrame, H2OModel, or character")
    }
  }

  #remove object from R client if possible (not possible for input of strings)
  ids <- deparse(substitute(ids))
  if( exists(ids, envir=parent.frame()) ) rm(list=ids, envir=parent.frame())
}

#'
#' Get an R Reference to an H2O Dataset, that will NOT be GC'd by default
#'
#' Get the reference to a frame with the given id in the H2O instance.
#'
#' @param id A string indicating the unique frame of the dataset to retrieve.
#' @export
h2o.getFrame <- function(id) {
  fr <- .newH2OFrame(id,id,-1,-1)
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
  else if (!(model_category %in% c("Unknown", "Binomial", "Multinomial", "Ordinal", "Regression", "Clustering", "AutoEncoder", "DimReduction", "WordEmbedding", "CoxPH", "AnomalyDetection")))
    stop(paste0("model_category, \"", model_category,"\", missing in the output"))
  Class <- paste0("H2O", model_category, "Model")
  model <- json$output[!(names(json$output) %in% c("__meta", "model_category"))]
  MetricsClass <- paste0("H2O", model_category, "Metrics")
  # setup the metrics objects inside of model...
  model$training_metrics   <- new(MetricsClass, algorithm=json$algo, on_train=TRUE, on_valid=FALSE, on_xval=FALSE, metrics=model$training_metrics)
  model$validation_metrics <- new(MetricsClass, algorithm=json$algo, on_train=FALSE, on_valid=TRUE, on_xval=FALSE, metrics=model$validation_metrics)
  model$cross_validation_metrics <- new(MetricsClass, algorithm=json$algo, on_train=FALSE, on_valid=FALSE, on_xval=TRUE, metrics=model$cross_validation_metrics)
  if (model_category %in% c("Binomial", "Multinomial", "Ordinal", "Regression")) { # add the missing metrics manually where
    model$coefficients <- model$coefficients_table[,2]
    names(model$coefficients) <- model$coefficients_table[,1]
  }
  parameters <- list()
  allparams  <- list()
  lapply(json$parameters, function(param) {
    if (!is.null(param$actual_value)) {
      name <- param$name
      value <- param$actual_value
      mapping <- .type.map[param$type,]
      type    <- mapping[1L, 1L]
      scalar  <- mapping[1L, 2L]

      if(type == "numeric" && class(value) == "list" && length(value) == 0) #Special case when using deep learning with 0 hidden units
        value <- 0
      else if (type == "numeric" && value == "Infinity")
        value <- Inf
      else if (type == "numeric" && value == "-Infinity")
        value <- -Inf

      # Parse frame information to a key
      if (type == "H2OFrame")
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
               have_pojo     = json$have_pojo,
               have_mojo     = json$have_mojo,
               model         = model)
}

#'
#' Download the Scoring POJO (Plain Old Java Object) of an H2O Model
#'
#' @param model An H2OModel
#' @param path The path to the directory to store the POJO (no trailing slash). If NULL, then print to
#'             to console. The file name will be a compilable java file name.
#' @param get_jar Whether to also download the h2o-genmodel.jar file needed to compile the POJO
#' @param getjar (DEPRECATED) Whether to also download the h2o-genmodel.jar file needed to compile the POJO. This argument is now called `get_jar`.
#' @param jar_name Custom name of genmodel jar.
#' @return If path is NULL, then pretty print the POJO to the console.
#'         Otherwise save it to the specified directory and return POJO file name.
#' @examples
#' \donttest{
#' library(h2o)
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#' my_model <- h2o.gbm(x=1:4, y=5, training_frame=fr)
#'
#' h2o.download_pojo(my_model)  # print the model to screen
#' # h2o.download_pojo(my_model, getwd())  # save the POJO and jar file to the current working
#' #                                         directory, NOT RUN
#' # h2o.download_pojo(my_model, getwd(), get_jar = FALSE )  # save only the POJO to the current
#' #                                                           working directory, NOT RUN
#' h2o.download_pojo(my_model, getwd())  # save to the current working directory
#' }
#' @export
h2o.download_pojo <- function(model, path=NULL, getjar=NULL, get_jar=TRUE, jar_name="") {
  
  if (class(model) == "H2OAutoML") {
    model <- model@leader
  }
  
  if (!(model@have_pojo)){
    stop(paste0(model@algrithm, ' does not support export to POJO'))
  }
  if(!is.null(path) && !(is.character(path))){
    stop("The 'path' variable should be of type character")
  }
  if(!(is.logical(get_jar))){
    stop("The 'get_jar' variable should be of type logical/boolean")
  }
  if(!is.null(path) && !(file.exists(path))){
    stop(paste0("'path',",path,", to save pojo cannot be found."))
  }

  #Get model id
  model_id <- model@model_id

  #Perform a safe (i.e. error-checked) HTTP GET request to an H2O cluster with POJO URL
  java <- .h2o.doSafeGET(urlSuffix = paste0(.h2o.__MODELS, ".java/", model_id))

  # HACK: munge model._id so that it conforms to Java class name. For example, change K-means to K_means.
  # TODO: clients should extract Java class name from header.
  pojoname = gsub("[+\\-* !@#$%^&()={}\\[\\]|;:'\"<>,.?/]","_",model_id,perl=T)

  #Path to save POJO, if `path` is provided
  file_path <- file.path(path, paste0(pojoname, ".java"))

  if( is.null(path) ){
    cat(java) #Pretty print POJO
  } else {
    write(java, file=file_path) #Write POJO to specified path
  # getjar is now deprecated and the new arg name is get_jar
  if (!is.null(getjar)) {
    warning("The `getjar` argument is DEPRECATED; use `get_jar` instead as `getjar` will eventually be removed")
    get_jar = getjar
    getjar = NULL
  }
  if (get_jar) {
    urlSuffix = "h2o-genmodel.jar"
    #Build genmodel.jar file path
    if(jar_name==""){
      jar.path <- file.path(path, "h2o-genmodel.jar")
    }else{
      jar.path <- file.path(path, jar_name)
    }
    #Perform a safe (i.e. error-checked) HTTP GET request to an H2O cluster with genmodel.jar URL
    #and write to jar.path.
    writeBin(.h2o.doSafeGET(urlSuffix = urlSuffix, binary = TRUE), jar.path, useBytes = TRUE)
  }
  return(paste0(pojoname,".java"))
  }
}

#'
#' Download the model in MOJO format.
#'
#' @param model An H2OModel
#' @param path The path where MOJO file should be saved. Saved to current directory by default.
#' @param get_genmodel_jar If TRUE, then also download h2o-genmodel.jar and store it in either in the same folder
#         as the MOJO or in ``genmodel_path`` if specified.
#' @param genmodel_name Custom name of genmodel jar.
#' @param genmodel_path Path to store h2o-genmodel.jar. If left blank and ``get_genmodel_jar`` is TRUE, then the h2o-genmodel.jar
#         is saved to ``path``.
#' @return Name of the MOJO file written to the path.
#'
#' @examples
#' \donttest{
#' library(h2o)
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#' my_model <- h2o.gbm(x=1:4, y=5, training_frame=fr)
#' h2o.download_mojo(my_model)  # save to the current working directory
#' }
#' @export
h2o.download_mojo <- function(model, path=getwd(), get_genmodel_jar=FALSE, genmodel_name="", genmodel_path="") {
  
  if (class(model) == "H2OAutoML") {
    model <- model@leader
  }
  
  if (!(model@have_mojo)){
    stop(paste0(model@algorithm, ' does not support export to MOJO'))
  }
  if(!(is.character(path))){
    stop("The 'path' variable should be of type character")
  }
  if(!(is.logical(get_genmodel_jar))){
    stop("The 'get_genmodel_jar' variable should be of type logical/boolean")
  }

  if(!(file.exists(path))){
    stop(paste0("'path',",path,", to save MOJO file cannot be found."))
  }

  if(genmodel_path=="") {
    genmodel_path <- path
  }

  if(!(file.exists(genmodel_path))){
    stop(paste0("'genmodel_path',",genmodel_path,", to save the genmodel.jar file cannot be found."))
  }

  #Get model id
  model_id <- model@model_id

  #Build URL for MOJO
  urlSuffix <- paste0(.h2o.__MODELS,"/",URLencode(model_id),"/mojo")

  #Build MOJO file path and download MOJO file & perform a safe (i.e. error-checked)
  #HTTP GET request to an H2O cluster with MOJO URL
  mojo.path <- file.path(path, paste0(model_id,".zip"))
  writeBin(.h2o.doSafeGET(urlSuffix = urlSuffix, binary = TRUE), mojo.path, useBytes = TRUE)

  if (get_genmodel_jar) {
    urlSuffix = "h2o-genmodel.jar"
    #Build genmodel.jar file path
    if(genmodel_name==""){
      jar.path <- file.path(genmodel_path, "h2o-genmodel.jar")
    }else{
      jar.path <- file.path(genmodel_path, genmodel_name)
    }
    #Perform a safe (i.e. error-checked) HTTP GET request to an H2O cluster with genmodel.jar URL
    #and write to jar.path.
    writeBin(.h2o.doSafeGET(urlSuffix = urlSuffix, binary = TRUE), jar.path, useBytes = TRUE)
  }
  return(paste0(model_id,".zip"))
}
