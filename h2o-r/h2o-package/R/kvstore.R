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
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
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
#' Retains models, frames and vectors specified in retained_elements argument.
#' Retained elements must be instances/ids of models and frames only. For models retained, training and validation frames are retained as well.
#' Cross validation models of a retained model are NOT retained automatically, those must be specified explicitely.
#'
#' @param timeout_secs Timeout in seconds. Default is no timeout.
#' @param retained_elements Instances or ids of models and frames to be retained. Combination of instances and ids in the same list is also a valid input.
#' @seealso \code{\link{h2o.rm}}
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' h2o.ls()
#' h2o.removeAll()
#' h2o.ls()
#' }
#' @export
h2o.removeAll <- function(timeout_secs=0, retained_elements = c()) {
  gc()
  tryCatch(
    {
    retained_keys <- list()
    
    for (element in retained_elements) {
      if (is(element, "H2OModel")) {
        retained_keys <- append(retained_keys, element@model_id)
      } else if (is.H2OFrame(element)) {
        retained_keys <- append(retained_keys, h2o.getId(element))
      } else if( is.character(element) ) {
        retained_keys <- append(retained_keys, element)
      } else {
        stop("The 'retained_elements' variable must be either an instance of H2OModel/H2OFrame or an id of H2OModel/H2OFrame.")
      }
    }
    
    parms <- list()
    parms$retained_keys <- paste0("[", paste(retained_keys, collapse=','), "]")
    
    .h2o.__remoteSend(.h2o.__DKV, method = "DELETE", timeout=timeout_secs, .params = parms)
    # remove all will also destroy all sessions, explicitly start a new one with a new ID
    invisible(.attach.new.session(h2o.getConnection()))
    },
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
#' @param cascade Boolean, if set to TRUE (default), the object dependencies (e.g. submodels) are also removed.
#' @seealso \code{\link{h2o.assign}}, \code{\link{h2o.ls}}
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' iris <- as.h2o(iris)
#' model <- h2o.glm(1:4,5,training = iris, family = "multinomial")
#' h2o.rm(iris)
#' }
#' @export
h2o.rm <- function(ids, cascade=TRUE) {
  gc()
  if( !is.vector(ids) ) x_list = c(ids) else x_list = ids
  for (xi in x_list) {
    if( is.null(xi) ) stop("h2o.rm with NULL object is not supported")
    if( is.H2OFrame(xi) ) {
      key <- h2o.keyof(xi) # String or None
      if( is.null(key) ) return() # Lazy frame, never evaluated, nothing in cluster
      .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=paste0("(rm ",key,")"), session_id=h2o.getConnection()@mutable$session_id, method = "POST")
    } else if( is(xi, "Keyed") ) {
      .h2o.__remoteSend(paste0(.h2o.__DKV, "/",h2o.keyof(xi)), method = "DELETE", .params=list(cascade=cascade))
    } else if( is.character(xi) ) {
      .h2o.__remoteSend(paste0(.h2o.__DKV, "/",xi), method = "DELETE", .params=list(cascade=cascade))
    } else {
      stop("Input to h2o.rm must be either an instance of H2OModel/H2OFrame or a character")
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
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_train.csv"
#' train <- h2o.importFile(f)
#' y <- "species"
#' x <- setdiff(names(train), y)
#' train[, y] <- as.factor(train[, y])
#' nfolds <- 5
#' num_base_models <- 2
#' my_gbm <- h2o.gbm(x = x, y = y, training_frame = train, 
#'                   distribution = "multinomial", ntrees = 10, 
#'                   max_depth = 3, min_rows = 2, learn_rate = 0.2, 
#'                   nfolds = nfolds, fold_assignment = "Modulo", 
#'                   keep_cross_validation_predictions = TRUE, seed = 1)
#' my_rf <- h2o.randomForest(x = x, y = y, training_frame = train, 
#'                           ntrees = 50, nfolds = nfolds, fold_assignment = "Modulo", 
#'                           keep_cross_validation_predictions = TRUE, seed = 1)
#' stack <- h2o.stackedEnsemble(x = x, y = y, training_frame = train, 
#'                              model_id = "my_ensemble_l1", 
#'                              base_models = list(my_gbm@model_id, my_rf@model_id), 
#'                              keep_levelone_frame = TRUE)
#' h2o.getFrame(stack@model$levelone_frame_id$name)
#' }
#' @export
h2o.getFrame <- function(id) {
  fr <- .newH2OFrame(id,id,-1,-1)
  .fetch.data(fr,1L)
  fr
}

#' Get an list of all model ids present in the cluster
#'
#' @return Returns a vector of model ids.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' iris_hf <- as.h2o(iris)
#' model_id <- h2o.gbm(x = 1:4, y = 5, training_frame = iris_hf)@@model_id
#' model_id_list <- h2o.list_models()
#' }
#' @export
h2o.list_models <- function() {
    models_json <- .h2o.__remoteSend(method = "GET", paste0(.h2o.__MODELS))$models
    res <- NULL
    for (json in models_json) {
        res <- c(res, json$model_id$name)
    }
    res
}

#' Get an R reference to an H2O model
#'
#' Returns a reference to an existing model in the H2O instance.
#'
#' @param model_id A string indicating the unique model_id of the model to retrieve.
#' @return Returns an object that is a subclass of \linkS4class{H2OModel}.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' iris_hf <- as.h2o(iris)
#' model_id <- h2o.gbm(x = 1:4, y = 5, training_frame = iris_hf)@@model_id
#' model_retrieved <- h2o.getModel(model_id)
#' }
#' @export
h2o.getModel <- function(model_id) {
  json <- .h2o.__remoteSend(method = "GET", paste0(.h2o.__MODELS, "/", model_id))$models[[1L]]
  model_category <- json$output$model_category
  if (is.null(model_category))
    model_category <- "Unknown"
  else if (!(model_category %in% c("Unknown", "Binomial", "BinomialUplift", "Multinomial", "Ordinal", "Regression", "Clustering", "AutoEncoder", "DimReduction", "WordEmbedding", "CoxPH", "AnomalyDetection", "TargetEncoder")))
    stop(paste0("model_category, \"", model_category,"\", missing in the output"))
  Class <- paste0("H2O", model_category, "Model")
  model <- json$output[!(names(json$output) %in% c("__meta", "model_category"))]
  MetricsClass <- paste0("H2O", model_category, "Metrics")
  # setup the metrics objects inside of model...
  model$training_metrics   <- new(MetricsClass, algorithm=json$algo, on_train=TRUE, on_valid=FALSE, on_xval=FALSE, metrics=model$training_metrics)
  model$validation_metrics <- new(MetricsClass, algorithm=json$algo, on_train=FALSE, on_valid=TRUE, on_xval=FALSE, metrics=model$validation_metrics)
  model$cross_validation_metrics <- new(MetricsClass, algorithm=json$algo, on_train=FALSE, on_valid=FALSE, on_xval=TRUE, metrics=model$cross_validation_metrics)
  if (model_category %in% c("Binomial", "Multinomial", "Ordinal", "Regression")) { # add the missing metrics manually where
    if (!is.null(model$coefficients_table)) {
      if (typeof(model$coefficients_table[[2]])=="double") {
        model$coefficients <- model$coefficients_table[,2]
        names(model$coefficients) <- model$coefficients_table[,1]
        if (!is.null(model$random_coefficients_table)) {
          model$random_coefficients <- model$random_coefficients_table[,2]
          names(model$random_coefficients) <- model$random_coefficients_table[,1]
        }
      } else { # with AnovaGLM
        coefLen <- length(model$coefficients_table)
        model$coefficients <- vector("list", coefLen)
        for (index in 1:coefLen) {
          model$coefficients[[index]] <- model$coefficients_table[[index]]
        }  
      }
    }
  }
  parameters <- list()
  allparams  <- list()

  fill_pairs <- function(param, all=TRUE) {
    if (!is.null(param$actual_value) && !is.null(param$name)) {
      name <- param$name
      value <- param$actual_value
      mapping <- .type.map[param$type,]
      type    <- mapping$type
      scalar  <- mapping$scalar

      if (type == "numeric" && inherits(value, "list") && length(value) == 0) #Special case when using deep learning with 0 hidden units
        value <- 0
      else if (type == "numeric") {
        value[value == "Infinity"] <- Inf
        value[value == "-Infinity"] <- -Inf

        # if there is no loss of information for integers, convert to numeric
        num_value <- as.numeric(value)
        if (is.character(value) && all(
          grepl(".", value, fixed = TRUE) | # Not an integer
          !is.finite(num_value) | # Or not a finite number (NaN/Inf)
          (-2^.Machine$double.digits <= num_value & # Or lies between the min and max fully representable number
            num_value <= 2^.Machine$double.digits)))
          value <- num_value
      }
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
        
      if (all == TRUE) {
        return(list(name, value))
      }
        
      # Store only user changed parameters into parameters
      # TODO: Should we use !isTrue(all.equal(param$default_value, param$actual_value)) instead?
      if (is.null(param$default_value) || param$required || !identical(param$default_value, param$actual_value)){
        return(list(name, value))
      }
    }
    return(NULL)
  }
    
  # get name, value pairs
  allparams_key_val <- lapply(json$parameters, fill_pairs, all=TRUE)
  parameters_key_val <- lapply(json$parameters, fill_pairs, all=FALSE)
    
  # remove NULLs
  allparams_key_val[sapply(allparams_key_val, is.null)] <- NULL
  parameters_key_val[sapply(parameters_key_val, is.null)] <- NULL
    
  # fill allparams, parameters
  for (param in allparams_key_val) {if (!any(is.na(param[1]))) allparams[unlist(param[1])] <- param[2]}
  for (param in parameters_key_val) {if (!any(is.na(param[1]))) parameters[unlist(param[1])] <- param[2]}

  # Run model specific hooks
  model_fill_func <- paste0(".h2o.fill_", json$algo)
  if (exists(model_fill_func, mode="function")) {
    model <- do.call(model_fill_func, list(model, parameters, allparams))
  }

  # Convert ignored_columns/response_column to valid R x/y


  parameters$x <- if (is.null(json$output$original_names)) json$output$names else json$output$original_names
  allparams$x  <- if (is.null(json$output$original_names)) json$output$names else json$output$original_names
    
  if (!is.null(parameters$response_column))
  {
    parameters$y <- parameters$response_column
    allparams$y <- allparams$response_column
    .not_x <- function(params) {
       c(params$y, params$ignored_columns, params$fold_column$column_name, params$weights_column$column_name,
         params$offset_column$column_name)
    }
    parameters$x <- setdiff(parameters$x, .not_x(parameters))
    allparams$x <- setdiff(allparams$x, .not_x(allparams))
  }
  allparams$ignored_columns <- NULL
  allparams$response_column <- NULL
  parameters$ignored_columns <- NULL
  parameters$response_column <- NULL


  params_to_select <- list(
    model_id = "name",
    response_column = "column_name",
    training_frame = "name",
    validation_frame = "name"
  )
  params <- list(actual=list(), default=list(), input=list())
  for (p in json$parameters) {
    if (p$name %in% names(params_to_select)) {
      params[["actual"]][[p$name]] <- p[["actual_value"]][[params_to_select[[p$name]]]]
      params[["default"]][[p$name]] <- p[["default_value"]][[params_to_select[[p$name]]]]
      params[["input"]][[p$name]] <- p[["input_value"]][[params_to_select[[p$name]]]]
    } else {
      params[["actual"]][[p$name]] <- p[["actual_value"]]
      params[["default"]][[p$name]] <- p[["default_value"]]
      params[["input"]][[p$name]] <- p[["input_value"]]
    }
  }

  if (identical("glm", json$algo) && allparams$HGLM) {
    .newH2OModel(Class         = Class,
                 model_id      = model_id,
                 algorithm     = json$algo,
                 parameters    = parameters,
                 allparameters = allparams,
                 params        = params,
                 have_pojo     = FALSE,
                 have_mojo     = FALSE,
                 model         = model)
  } else {
  .newH2OModel(Class         = Class,
               model_id      = model_id,
               algorithm     = json$algo,
               parameters    = parameters,
               allparameters = allparams,
               params        = params,
               have_pojo     = json$have_pojo,
               have_mojo     = json$have_mojo,
               model         = model)
  }
}

#' Retrieves an instance of \linkS4class{H2OSegmentModels} for a given id.
#'
#' @param segment_models_id A string indicating the unique segment_models_id
#         of the collections of segment-models to retrieve.
#' @return Returns an object that is a subclass of \linkS4class{H2OSegmentModels}.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' iris_hf <- as.h2o(iris)
#' h2o.train_segments(algorithm = "gbm",
#'                    segment_columns = "Species", segment_models_id="models_by_species",
#'                    x = c(1:3), y = 4, training_frame = iris_hf, ntrees = 5, max_depth = 4)
#' models <- h2o.get_segment_models("models_by_species")
#' as.data.frame(models)
#' }
#' @export
h2o.get_segment_models <- function(segment_models_id) {
  new("H2OSegmentModels", segment_models_id=segment_models_id)
}

#' Converts a collection of Segment Models to a data.frame
#'
#' @param x Object of class \linkS4class{H2OSegmentModels}.
#' @param ... Further arguments to be passed down from other methods.
#' @return Returns data.frame with result of segment model training.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' iris_hf <- as.h2o(iris)
#' models <- h2o.train_segments(algorithm = "gbm",
#'                              segment_columns = "Species",
#'                              x = c(1:3), y = 4,
#'                              training_frame = iris_hf,
#'                              ntrees = 5,
#'                              max_depth = 4)
#' as.data.frame(models)
#' }
#' @export
as.data.frame.H2OSegmentModels <- function(x, ...) {
  as.data.frame(.newExpr("segment_models_as_frame", x@segment_models_id))
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
#' \dontrun{
#' library(h2o)
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#' my_model <- h2o.gbm(x = 1:4, y = 5, training_frame = fr)
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
  
  if (inherits(model, "H2OAutoML")) {
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
#'         as the MOJO or in ``genmodel_path`` if specified.
#' @param genmodel_name Custom name of genmodel jar.
#' @param genmodel_path Path to store h2o-genmodel.jar. If left blank and ``get_genmodel_jar`` is TRUE, then the h2o-genmodel.jar
#'         is saved to ``path``.
#' @param filename string indicating the file name. (Type of file is always .zip)
#' @return Name of the MOJO file written to the path.
#'
#' @examples
#' \dontrun{
#' library(h2o)
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#' my_model <- h2o.gbm(x = 1:4, y = 5, training_frame = fr)
#' h2o.download_mojo(my_model)  # save to the current working directory
#' }
#' @export
h2o.download_mojo <- function(model, path=getwd(), get_genmodel_jar=FALSE, genmodel_name="", genmodel_path="", filename="") {
  
  if (inherits(model, "H2OAutoML")) {
    model <- model@leader
  }

  if(!is(model, "H2OModel")) {
    stop("`model` must be an H2OModel object")
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

  if(filename == "") {
    filename <- paste0(model@model_id, ".zip")
  }

  #Build URL for MOJO
  urlSuffix <- paste0(.h2o.__MODELS,"/",URLencode(model@model_id),"/mojo")

  #Build MOJO file path and download MOJO file & perform a safe (i.e. error-checked)
  #HTTP GET request to an H2O cluster with MOJO URL
  mojo.path <- file.path(path, filename)
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
  return(filename)
}

#'
#' Download the model in binary format.
#' The owner of the file saved is the user by which python session was executed.
#'
#' @param model An H2OModel
#' @param path The path where binary file should be downloaded. Downloaded to current directory by default.
#' @param export_cross_validation_predictions A boolean flag indicating whether the download model should be
#'      saved with CV Holdout Frame predictions. Default is not to export the predictions. 
#' @param filename string indicating the file name.
#'
#' @examples
#' \dontrun{
#' library(h2o)
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#' my_model <- h2o.gbm(x = 1:4, y = 5, training_frame = fr)
#' h2o.download_model(my_model)  # save to the current working directory
#' }
#' @export
h2o.download_model <- function(model, path=NULL, export_cross_validation_predictions=FALSE, filename="") {

    if(!is(model, "H2OModel")) {
      stop("`model` must be an H2OModel object")
    }

    if(!is.null(path) && !(is.character(path))){
      stop("The 'path' variable should be of type character")
    }
    if(!is.null(path) && !(file.exists(path))){
      stop(paste0("'path',",path,", to save pojo cannot be found."))
    }
    if(is.null(path)){
      path = getwd()
    }
    if(!is.logical(export_cross_validation_predictions)){
      stop("The 'export_cross_validation_predictions' variable should be of type logical")
    }

    if(filename == "") {
      filename <- model@model_id
    }

    #prepare suffix to get the right endpoint
    urlSuffix = paste0(.h2o.__MODELS, ".fetch.bin/", model@model_id)
    
    #Path to save model, if `path` is provided
    file_path <- file.path(path, filename)
    parms <- list(export_cross_validation_predictions=export_cross_validation_predictions)
    writeBin(.h2o.doSafeGET(urlSuffix = urlSuffix, binary = TRUE, parms = parms), file_path, useBytes = TRUE)
    
    return(file_path)
}

#'
#' Execute a Rapids expression.
#'
#' @param expr The rapids expression (ascii string)
#'
#' @examples
#' \dontrun{
#' h2o.rapids('(setproperty "sys.ai.h2o.algos.evaluate_auto_model_parameters" "true")')
#' }
#' @export
h2o.rapids <- function(expr) {
    res <- .h2o.__remoteSend(.h2o.__RAPIDS, h2oRestApiVersion = 99, ast=paste0(expr), session_id=h2o.getConnection()@mutable$session_id, method = "POST")
}
