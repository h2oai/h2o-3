#
# H2O Model Related Functions
#
#' @importFrom graphics strwidth par legend polygon arrows points grid
#' @importFrom grDevices dev.copy dev.off png rainbow adjustcolor
#' @include classes.R
NULL

#-----------------------------------------------------------------------------------------------------------------------
#   Helper Functions
#-----------------------------------------------------------------------------------------------------------------------

#'
#' Used to verify data, x, y and turn into the appropriate things
#'
#' @param data H2OFrame
#' @param x features
#' @param y response
#' @param autoencoder autoencoder flag
.verify_dataxy <- function(data, x, y, autoencoder = FALSE) {
   if (is(x, "H2OInfogram"))
     x<-x@admissible_features
  if(!is.null(x) && !is.character(x) && !is.numeric(x)) # only check if x is not null
    stop('`x` must be column names or indices')
  if( !autoencoder )
    if(!is.character(y) && !is.numeric(y))
      stop('`y` must be a column name or index')

  cc <- colnames(chk.H2OFrame(data))
  
  if (!is.null(x)) {
    if(is.character(x)) {
      if(!all(x %in% cc))
        stop("Invalid column names: ", paste(x[!(x %in% cc)], collapse = ','))
      x_i <- match(x, cc)
    } else {
      if(any( x < 1L | x > attr(x,'ncol')))
        stop('out of range explanatory variable ', paste(x[x < 1L | x > length(cc)], collapse = ','))
      x_i <- x
      x <- cc[x_i]
    }
  } else {
    x_i <- NULL
  }

  x_ignore <- c()
  if( !autoencoder ) {
    if(is.character(y)){
      if(!(y %in% cc))
        stop(y, ' is not a column name')
      y_i <- which(y == cc)
    } else {
      if(y < 1L || y > length(cc))
        stop('response variable index ', y, ' is out of range')
      y_i <- y
      y <- cc[y]
    }

    if(!is.null(x) && !autoencoder && (y %in% x)) {
      warning('removing response variable from the explanatory variables')
      x <- setdiff(x,y)
    }
    x_ignore <- setdiff(setdiff(cc, x), y)
    if( length(x_ignore) == 0L ) x_ignore <- ''
    return(list(x=x, y=y, x_i=x_i, x_ignore=x_ignore, y_i=y_i))
  } else {
    x_ignore <- setdiff(cc, x)
    if( !missing(y) ) stop("`y` should not be specified for autoencoder=TRUE, remove `y` input")
    return(list(x=x,x_i=x_i,x_ignore=x_ignore))
  }
}

.verify_datacols <- function(data, cols) {
  if(!is.character(cols) && !is.numeric(cols))
    stop('`cols` must be column names or indices')

  cc <- colnames(chk.H2OFrame(data))
  if(length(cols) == 1L && cols == '')
    cols <- cc
  if(is.character(cols)) {
    if(!all(cols %in% cc))
      stop("Invalid column names: ", paste(cols[which(!cols %in% cc)], collapse=", "))
    cols_ind <- match(cols, cc)
  } else {
    if(any(cols < 1L | cols > length(cc)))
      stop('out of range explanatory variable ', paste(cols[cols < 1L | cols > length(cc)], collapse=','))
    cols_ind <- cols
    cols <- cc[cols_ind]
  }

  cols_ignore <- setdiff(cc, cols)
  if( length(cols_ignore) == 0L )
    cols_ignore <- ''
  list(cols=cols, cols_ind=cols_ind, cols_ignore=cols_ignore)
}

.build_cm <- function(cm, actual_names = NULL, predict_names = actual_names, transpose = TRUE) {
  categories <- length(cm)
  cf_matrix <- matrix(unlist(cm), nrow=categories)
  if(transpose)
    cf_matrix <- t(cf_matrix)

  cf_total <- apply(cf_matrix, 2L, sum)
  cf_error <- c(1 - diag(cf_matrix)/apply(cf_matrix,1L,sum), 1 - sum(diag(cf_matrix))/sum(cf_matrix))
  cf_matrix <- rbind(cf_matrix, cf_total)
  cf_matrix <- cbind(cf_matrix, round(cf_error, 3L))

  if(!is.null(actual_names))
    dimnames(cf_matrix) = list(Actual = c(actual_names, "Totals"), Predicted = c(predict_names, "Error"))
  cf_matrix
}


.h2o.modelJob <- function( algo, params, h2oRestApiVersion=.h2o.__REST_API_VERSION, verbose=FALSE) {
  if( !is.null(params$validation_frame) )
    .eval.frame(params$training_frame)
  if( !is.null(params$validation_frame) )
    .eval.frame(params$validation_frame)
  if (length(grep("stopping_metric", attributes(params)))>0) {
    if (params$stopping_metric=="r2")
      stop("r2 cannot be used as an early stopping_metric yet.  Check this JIRA https://github.com/h2oai/h2o-3/issues/12248 for progress.")
  }
  if (algo=="pca" && is.null(params$k)) # make sure to set k=1 for default for pca
    params$k=1
  job <- .h2o.startModelJob(algo, params, h2oRestApiVersion)
  .h2o.getFutureModel(job, verbose = verbose)
}

.h2o.startModelJob <- function(algo, params, h2oRestApiVersion) {
  .key.validate(params$key)
  #---------- Params ----------#
  param_values <- .h2o.makeModelParams(algo, params, h2oRestApiVersion)
  #---------- Build! ----------#
  res <- .h2o.__remoteSend(method = "POST", .h2o.__MODEL_BUILDERS(algo), .params = param_values, h2oRestApiVersion = h2oRestApiVersion)
  .h2o.processResponseWarnings(res)
  #---------- Output ----------#
  job_key  <- res$job$key$name
  dest_key <- res$job$dest$name
  new("H2OModelFuture",job_key=job_key, model_id=dest_key)
}

.h2o.makeModelParams <- function(algo, params, h2oRestApiVersion) {
  #---------- Force evaluate temporary ASTs ----------#
  ALL_PARAMS <- .h2o.__remoteSend(method = "GET", h2oRestApiVersion = h2oRestApiVersion, .h2o.__MODEL_BUILDERS(algo))$model_builders[[algo]]$parameters
  #---------- Check user parameter types ----------#
  param_values <- .h2o.checkAndUnifyModelParameters(algo = algo, allParams = ALL_PARAMS, params = params)
  #---------- Validate parameters ----------#
  #.h2o.validateModelParameters(algo, param_values, h2oRestApiVersion)
  return(param_values)
}

.h2o.processResponseWarnings <- function(res) {
  if(length(res$messages) != 0L){
    warn <- lapply(res$messages, function(y) {
      if(is.list(y) && y$message_type == "WARN" )
        paste0(y$message, ".\n")
      else ""
    })
    if(any(nzchar(warn))) warning(warn)
  }
}

.h2o.startSegmentModelsJob <- function(algo, segment_params, params, h2oRestApiVersion) {
  #---------- Params ----------#
  param_values <- .h2o.makeModelParams(algo, params, h2oRestApiVersion)
  param_values$segment_models_id <- segment_params$segment_models_id
  param_values$segment_columns <- .collapse.char(segment_params$segment_columns)
  param_values$parallelism <- segment_params$parallelism 
  #---------- Build! ----------#
  job <- .h2o.__remoteSend(method = "POST", .h2o.__SEGMENT_MODELS_BUILDERS(algo), .params = param_values, h2oRestApiVersion = h2oRestApiVersion)
  job_key  <- job$key$name
  dest_key <- job$dest$name
  new("H2OSegmentModelsFuture",job_key=job_key, segment_models_id=dest_key)
}

.h2o.segmentModelsJob <- function(algo, segment_params, params, h2oRestApiVersion) {
  .key.validate(segment_params$segment_models_id)
  sm <- .h2o.startSegmentModelsJob(algo, segment_params, params, h2oRestApiVersion)
  .h2o.getFutureSegmentModels(sm)
}

.h2o.getFutureSegmentModels <- function(object) {
  .h2o.__waitOnJob(object@job_key)
  h2o.get_segment_models(object@segment_models_id)
}

#
# Validate given parameters against algorithm parameters validation
# REST end-point. Stop execution in case of validation error.
#
.h2o.validateModelParameters <- function(algo, params, h2oRestApiVersion = .h2o.__REST_API_VERSION) {
  validation <- .h2o.__remoteSend(method = "POST", paste0(.h2o.__MODEL_BUILDERS(algo), "/parameters"), .params = params, h2oRestApiVersion = h2oRestApiVersion)
  if(length(validation$messages) != 0L) {
    error <- lapply(validation$messages, function(x) {
      if( x$message_type == "ERRR" )
        paste0(x$message, ".\n")
      else ""
    })
    if(any(nzchar(error))) stop(error)
    warn <- lapply(validation$messages, function(i) {
      if( i$message_type == "WARN" )
        paste0(i$message, ".\n")
      else ""
    })
    if(any(nzchar(warn))) warning(warn)
  }
}

.h2o.createModel <- function(algo, params, h2oRestApiVersion = .h2o.__REST_API_VERSION) {
  .h2o.getFutureModel(.h2o.startModelJob(algo, params, h2oRestApiVersion))
}

.h2o.pollModelUpdates <- function(job) {
  cat(paste0("\nScoring History for Model ",job$dest$name, " at ", Sys.time(),"\n"))
  print(paste0("Model Build is ", job$progress*100, "% done..."))
  if(!is.null(job$progress_msg)){
  #   print(tail(h2o.getModel(job$dest$name)@model$scoring_history))
  }else{
    print("Scoring history is not available yet...") #Catch 404 with scoring history. Can occur when nfolds >=2
  }
}

.h2o.getFutureModel <- function(object, verbose=FALSE) {
  .h2o.__waitOnJob(object@job_key, pollUpdates=ifelse(verbose, .h2o.pollModelUpdates, as.null))
  h2o.getModel(object@model_id)
}

.h2o.prepareModelParameters <- function(algo, params, is_supervised) {
  if (!is.null(params$training_frame))
    params$training_frame <- chk.H2OFrame(params$training_frame)
  if (!is.null(params$validation_frame))
    params$validation_frame <- chk.H2OFrame(params$validation_frame)

  # Check if specified model request is for supervised algo
  isSupervised <- if (!is.null(is_supervised)) is_supervised else .isSupervised(algo, params)

  if (isSupervised) {
    if (!is.null(params$x)) { x <- params$x; params$x <- NULL }
    if (!is.null(params$y)) { y <- params$y; params$y <- NULL }
    args <- .verify_dataxy(params$training_frame, x, y)
    if( !is.null(params$offset_column) && !is.null(params$offset_column))  args$x_ignore <- args$x_ignore[!( params$offset_column == args$x_ignore )]
    if( !is.null(params$weights_column) && !is.null(params$weights_column)) args$x_ignore <- args$x_ignore[!( params$weights_column == args$x_ignore )]
    if( !is.null(params$fold_column) && !is.null(params$fold_column)) args$x_ignore <- args$x_ignore[!( params$fold_column == args$x_ignore )]
    params$ignored_columns <- args$x_ignore
    params$response_column <- args$y
  } else {
    if (!is.null(params$x)) {
      x <- params$x
      params$x <- NULL
      args <- .verify_datacols(params$training_frame, x)
      params$ignored_columns <- args$cols_ignore
    }
  }
  # Note: Magic copied from start .h2o.startModelJob
  params <- lapply(params, function(x) { if(is.integer(x)) x <- as.numeric(x); x })
  params
}

.h2o.getModelParameters <- function(algo, h2oRestApiVersion = .h2o.__REST_API_VERSION) {
  .h2o.__remoteSend(method = "GET", .h2o.__MODEL_BUILDERS(algo), h2oRestApiVersion = h2oRestApiVersion)$model_builders[[algo]]$parameters
}

.h2o.checkAndUnifyModelParameters <- function(algo, allParams, params, hyper_params = list()) {
  addGamCol <- FALSE
  if (algo == "gam") {# gam_column is specified in subspace and need to fake something here
    if (is.null(params$gam_columns) && !(is.null(hyper_params$subspaces)) && !(is.null(hyper_params$subspaces[[1]]$gam_columns))) {
      addGamCol <- TRUE
      params$gam_columns = list("C1")  # set default gam_columns
    }
  }
  # First verify all parameters
  error <- lapply(allParams, function(i) {
    e <- ""
    name <- i$name
    # R treats integer as not numeric
    if(is.integer(params[[name]])){
      params[[name]] <- as.numeric(params[[name]])
    }
    if (i$required && !((name %in% names(params)) || (name %in% names(hyper_params)))) {
      e <- paste0("argument \"", name, "\" is missing, with no default\n")
    } else if (name %in% names(params)) {
      e <- .h2o.checkParam(i, params[[name]])
      if (!nzchar(e)) {
        params[[name]] <<- .h2o.transformParam(i, params[[name]])
      }
    }
    e
  })

  if (addGamCol)
    params$gam_columns <- NULL
  
  if(any(nzchar(error)))
    stop(error)

  #---------- Create parameter list to pass ----------#
  param_values <- lapply(params, function(i) {
    if(is.H2OFrame(i))  h2o.getId(i)
    else             i
  })

  param_values
}

# Long precision
.is.int64 <- function(v) {
  number <- suppressWarnings(as.numeric(v))
  if(is.na(number)) FALSE
  else number > -2^63 & number < 2^63 & (floor(number)==ceiling(number))
}

# Precise int in double presision
.is.int53 <- function(v) {
    number <- suppressWarnings(as.numeric(v))
    if(is.na(number)) FALSE
    else number > -2^53 & number < 2^53 & (floor(number)==ceiling(number))
}

# Check definition of given parameters in given list of parameters
# Returns error message or empty string
# Note: this function has no side-effects!
.h2o.checkParam <- function(paramDef, paramValue) {
  e <- ""
  # Fetch mapping for given Java to R types
  mapping <- .type.map[paramDef$type,]
  type    <- mapping[1L, 1L]
  scalar  <- mapping[1L, 2L]
  name    <- paramDef$name
  if (is.na(type))
    stop("Cannot find type ", paramDef$type, " in .type.map")
  if (scalar) { # scalar == TRUE
    if (type == "H2OModel")
      type <-  "character"
      if (name == "seed") {
        if(is.character(paramValue) && !.is.int64(paramValue))
          e <- paste0("\"seed\" must be of type long or string long, but got a string which cannot be converted to long.\n")
        else if(is.numeric(paramValue)){
          if(!.is.int64(paramValue)){
            e <- paste0("\"seed\" must be of type long or string long, but got a number which cannot be converted to long.\n")
          } else if(!.is.int53(paramValue)) {
              warning("R can handle only 53-bit integer without loss. 
              If you need to use a less/larger number than the integer, pass seed parameter as the string number. Otherwise, the seed could be inconsistent.
              (For example, if you need to use autogenerated seed like -8664354335142703762 from H2O server.)")
          }
        }
      } else {
        if (!inherits(paramValue, type)) {
          e <- paste0(e, "\"", name , "\" must be of type ", type, ", but got ", class(paramValue), ".\n")
        } else if ((length(paramDef$values) > 1L) && (is.null(paramValue) || !(tolower(paramValue) %in% tolower(paramDef$values)))) {
          e <- paste0(e, "\"", name,"\" must be in")
          for (fact in paramDef$values)
            e <- paste0(e, " \"", fact, "\",")
          e <- paste(e, "but got", paramValue)
        }
      }
    } else {      # scalar == FALSE
      if (!inherits(paramValue, type))
        e <- paste0("vector of ", name, " must be of type ", type, ", but got ", class(paramValue), ".\n")
    }
  e
}

.h2o.transformParam <- function(paramDef, paramValue, collapseArrays = TRUE) {
  # Fetch mapping for given Java to R types
  mapping <- .type.map[paramDef$type,]
  type    <- mapping[1L, 1L]
  scalar  <- mapping[1L, 2L]
  name    <- paramDef$name
  if (scalar) { # scalar == TRUE
    if (inherits(paramValue, 'numeric') && paramValue ==  Inf) {
      paramValue <- "Infinity"
    } else if (inherits(paramValue, 'numeric') && paramValue == -Inf) {
      paramValue <- "-Infinity"
    }
  } else {      # scalar == FALSE
    if (inherits(paramValue, 'numeric')) {
        k = which(paramValue == Inf | paramValue == -Inf)
        if (length(k) > 0)
          for (n in k)
            if (paramValue[n] == Inf)
              paramValue[n] <- "Infinity"
            else
              paramValue[n] <- "-Infinity"
    }
    if (collapseArrays) {
      if(any(sapply(paramValue, function(x) !is.null(x) && is.H2OFrame(x))))
         paramValue <- lapply( paramValue, function(x) {
                            if (is.null(x)) NULL
                            else if (all(is.na(x))) NA
                            else paste0('"',h2o.getId(x),'"')
                          })
      if (paramDef$type == "string[][]"){
        paramValue <- .collapse.list.of.list.string(paramValue)
      } else if (type == "character")
        paramValue <- .collapse.char(paramValue)
      else if (paramDef$type == "StringPair[]")
        paramValue <- .collapse(sapply(paramValue, .collapse.tuple.string))
      else if (paramDef$type == "KeyValue[]") {
        f <- function(i) { .collapse.tuple.key_value(paramValue[i]) }
        paramValue <- .collapse(sapply(seq(length(paramValue)), f))
      } else
        paramValue <- .collapse(paramValue)
    }
  }
  if( is.H2OFrame(paramValue) )
    paramValue <- h2o.getId(paramValue)
  paramValue
}

.escape.string <- function(xi) { paste0("\"", xi, "\"") }

.collapse.tuple.string <- function(x) {
  .collapse.tuple(x, .escape.string)
}

.collapse.list.of.list.string <- function(x){
  parts <- c()
  for (i in x) {
    parts <- c(parts, paste0("[", paste0(i, collapse = ","), "]"))
  }
  paste0("[", paste0(parts, collapse = ","), "]")
}

.collapse.tuple.key_value <- function(x) {
  .collapse.tuple(list(
    key = .escape.string(names(x)),
    value = x[[1]]
  ), identity)
}

.collapse.tuple <- function(x, escape) {
  names <- names(x)
  if (is.null(names))
    names <- letters[1:length(x)]
  r <- c()
  for (i in 1:length(x)) {
    s <- paste0(names[i], ": ", escape(x[i]))
    r <- c(r, s)
  }
  paste0("{", paste0(r, collapse = ","), "}")
}

# Validate a given set of hyper parameters
# against algorithm definition.
# Transform all parameters in the same way as normal algorithm
# would do.
.h2o.checkAndUnifyHyperParameters <- function(algo, allParams, hyper_params, do_hyper_params_check) {

  errors <- lapply(allParams, function(paramDef) {
      e <- ""
      name <- paramDef$name
      hyper_names <- names(hyper_params)
      # First reject all non-gridable hyper parameters
      if (!paramDef$gridable && (name %in% hyper_names)) {
        e <- paste0("argument \"", name, "\" is not gridable\n")
      } else if (name %in% hyper_names) { # Check all specified hyper parameters
        # Hyper values for `name` parameter
        hyper_vals <- hyper_params[[name]]
        # Collect all possible verification errors
        if (do_hyper_params_check) {
          he <- lapply(hyper_vals, function(hv) {
                  # Transform all integer values to numeric
                  hv <- if (is.integer(hv)) as.numeric(hv) else hv
                  .h2o.checkParam(paramDef, hv)
                })
          e <- paste(he, collapse='')
        }
        # If there is no error then transform hyper values
        if (!nzchar(e)) {
          is_scalar <- .type.map[paramDef$type,][1L, 2L]
          transf_fce <- function(hv) {
                          # R does not treat integers as numeric
                          if (is.integer(hv)) {
                            hv <- as.numeric(hv)
                          }
                          mapping <- .type.map[paramDef$type,]
                          type <- mapping[1L, 1L]
                          # Note: we apply this transformatio also for types
                          # reported by the backend as scalar because of PUBDEV-1955
                          if (is.list(hv)) {
                            hv <- as.vector(hv, mode=type)
                          }
                          # Force evaluation of frames and fetch frame_id as
                          # a side effect
                          if (is.H2OFrame(hv) )
                            hv <- h2o.getId(hv)
                          .h2o.transformParam(paramDef, hv, collapseArrays = FALSE)
                        }
          transf_hyper_vals <- if (is_scalar) sapply(hyper_vals,transf_fce) else lapply(hyper_vals, transf_fce)
          hyper_params[[name]] <<- transf_hyper_vals
        }
      }
      e
  })

  if(any(nzchar(errors)))
    stop(errors)

  hyper_params
}

#' Predict on an H2O Model
#'
#' Obtains predictions from various fitted H2O model objects.
#'
#' This method dispatches on the type of H2O model to select the correct
#' prediction/scoring algorithm.
#' The order of the rows in the results is the same as the order in which the
#' data was loaded, even if some rows fail (for example, due to missing
#' values or unseen factor levels).
#'
#' @param object a fitted \linkS4class{H2OModel} object for which prediction is
#'        desired
#' @param newdata An H2OFrame object in which to look for
#'        variables with which to predict.
#' @param ... additional arguments to pass on.
#' @return Returns an H2OFrame object with probabilites and
#'         default predictions.
#' @seealso \code{\link{h2o.deeplearning}}, \code{\link{h2o.gbm}},
#'          \code{\link{h2o.glm}}, \code{\link{h2o.randomForest}} for model
#'          generation in h2o.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/glm_test/insurance.csv"
#' insurance <- h2o.importFile(f)
#' predictors <- colnames(insurance)[1:4]
#' response <- "Claims"
#' insurance['Group'] <- as.factor(insurance['Group'])
#' insurance['Age'] <- as.factor(insurance['Age'])
#' splits <- h2o.splitFrame(data =  insurance, ratios = 0.8, seed = 1234)
#' train <- splits[[1]]
#' valid <- splits[[2]]
#' insurance_gbm <- h2o.gbm(x = predictors, y = response, 
#'                          training_frame = train,
#'                          validation_frame = valid, 
#'                          distribution = "huber", 
#'                          huber_alpha = 0.9, seed = 1234)
#' h2o.predict(insurance_gbm, newdata = insurance)
#' }
#' @export
predict.H2OModel <- function(object, newdata, ...) {
  h2o.predict.H2OModel(object, newdata, ...)
}

#' Predict on an H2O Model
#'
#' @param object a fitted model object for which prediction is desired.
#' @param newdata An H2OFrame object in which to look for
#'        variables with which to predict.
#' @param ... additional arguments to pass on.
#' @return Returns an H2OFrame object with probabilites and
#'         default predictions.
#' @export
h2o.predict <- function(object, newdata, ...){
  UseMethod("h2o.predict", object)
}

#' Use H2O Transformation model and apply the underlying transformation
#'
#' @param model A trained model representing the transformation strategy
#' @param ... Transformation model-specific parameters
#' @return Returns an H2OFrame object with data transformed.
#' @export
setGeneric("h2o.transform", function(model, ...) {
  if(!is(model, "H2OModel")) {
    stop(paste("Argument 'model' must be an H2O Model. Received:", class(model)))
  }
  standardGeneric("h2o.transform")
})


#' Applies target encoding to a given dataset
#'
#' @param model A trained model representing the transformation strategy
#' @param data An H2OFrame with data to be transformed
#' @param blending Use blending during the transformation. Respects model settings when not set.
#' @param inflection_point Blending parameter. Only effective when blending is enabled.
#'  By default, model settings are respected, if not overridden by this setting.
#' @param smoothing Blending parameter. Only effective when blending is enabled.
#'  By default, model settings are respected, if not overridden by this setting.
#' @param noise An amount of random noise added to the encoding, this helps prevent overfitting.
#'  By default, model settings are respected, if not overridden by this setting.
#' @param as_training Must be set to True when encoding the training frame. Defaults to False.
#' @param ... Mainly used for backwards compatibility, to allow deprecated parameters.
#' @return Returns an H2OFrame object with data transformed.
#' @export
setMethod("h2o.transform", signature("H2OTargetEncoderModel"), function(model, data,
                                                                        blending = NULL,
                                                                        inflection_point = -1,
                                                                        smoothing = -1,
                                                                        noise = NULL,
                                                                        as_training = FALSE,
                                                                        ...) {
    varargs <- list(...)
    for (arg in names(varargs)) {
        if (arg %in% c('data_leakage_handling', 'seed')) {
            warning(paste0("argument '", arg, "' is deprecated and will be ignored; please define it instead on model creation using `h2o.targetencoder`."))
            argval <- varargs[[arg]]
            if (arg == 'data_leakage_handling' && argval != "None") {
                warning(paste0("Deprecated `data_leakage_handling=",argval,"` is replaced by `as_training=True`. ",
                        "Please update your code."))
                as_training <- TRUE
            }
        } else if (arg == 'use_blending') {
            warning("argument 'use_blending' is deprecated; please use 'blending' instead.")
            if (missing(blending)) blending <- varargs$use_blending else warning("ignoring 'use_blending' as 'blending' was also provided.")
        } else {
            stop(paste("unused argument", arg, "=", varargs[[arg]]))
        }
    }

    params <- list()
    params$model <- model@model_id
    params$frame <- h2o.getId(data)
    if (is.null(blending)){
        params$blending <- model@allparameters$blending
    } else {
        params$blending <- blending
    }
    if (params$blending) {
        params$inflection_point <- inflection_point
        params$smoothing <- smoothing
    }
    if (!is.null(noise)){
        params$noise <- noise
    }
    params$as_training <- as_training
    
    res <- .h2o.__remoteSend(
        "TargetEncoderTransform",
        method = "GET",
        h2oRestApiVersion = 3,.params = params
    )
  
    h2o.getFrame(res$name)
})

#'
#' Transform words (or sequences of words) to vectors using a word2vec model.
#'
#' @param model A word2vec model.
#' @param words An H2OFrame made of a single column containing source words.
#' @param aggregate_method Specifies how to aggregate sequences of words. If method is `NONE`
#'    then no aggregation is performed and each input word is mapped to a single word-vector.
#'    If method is 'AVERAGE' then input is treated as sequences of words delimited by NA.
#'    Each word of a sequences is internally mapped to a vector and vectors belonging to
#'    the same sentence are averaged and returned in the result.
#' @examples
#' \dontrun{
#' h2o.init()
#'
#' # Build a simple word2vec model
#' data <- as.character(as.h2o(c("a", "b", "a")))
#' w2v_model <- h2o.word2vec(data, sent_sample_rate = 0, min_word_freq = 0, epochs = 1, vec_size = 2)
#'
#' # Transform words to vectors without aggregation
#' sentences <- as.character(as.h2o(c("b", "c", "a", NA, "b")))
#' h2o.transform(w2v_model, sentences) # -> 5 rows total, 2 rows NA ("c" is not in the vocabulary)
#'
#' # Transform words to vectors and return average vector for each sentence
#' h2o.transform(w2v_model, sentences, aggregate_method = "AVERAGE") # -> 2 rows
#' }
#' @export
setMethod("h2o.transform", signature("H2OWordEmbeddingModel"), function(model, words, aggregate_method = c("NONE", "AVERAGE")) {
  
  if (!is(model, "H2OModel")) stop(paste("The argument 'model' must be a word2vec model. Received:", class(model)))
  if (missing(words)) stop("`words` must be specified")
  if (!is.H2OFrame(words)) stop("`words` must be an H2OFrame")
  if (ncol(words) != 1) stop("`words` frame must contain a single string column")
  
  if (length(aggregate_method) > 1)
    aggregate_method <- aggregate_method[1]
  
  res <- .h2o.__remoteSend(method="GET", "Word2VecTransform", model = model@model_id,
                           words_frame = h2o.getId(words), aggregate_method = aggregate_method)
  key <- res$vectors_frame$name
  h2o.getFrame(key)
  
})


#'
#' Transform the given data frame using the model if the latter supports transformations.
#' 
#' @param model A trained model representing the transformation strategy (currently supported algorithms are `glrm` and `pipeline`).
#' @param data An H2OFrame on which the transformation is applied.
#' @return an H2OFrame object representing the transformed data.
#' @export
setMethod("h2o.transform", signature("H2OModel"), function(model, data) {
    if (!model@algorithm %in% c("glrm", "pipeline")) stop("h2o.transform is not available for this type of model.")
    return(.newExpr("transform", model@model_id, h2o.getId(data)))
}) 

#'
#' @rdname predict.H2OModel
#' @export
h2o.predict.H2OModel <- function(object, newdata, ...) {
  if (missing(newdata)) {
    stop("predictions with a missing `newdata` argument is not implemented yet")
  }

  # Send keys to create predictions
  url <- paste0('Predictions/models/', object@model_id, '/frames/',  h2o.getId(newdata))
  res <- .h2o.__remoteSend(url, method = "POST", h2oRestApiVersion = 4)
  job_key <- res$key$name
  dest_key <- res$dest$name
  .h2o.__waitOnJob(job_key)
  h2o.getFrame(dest_key)
}

#' Predict the Leaf Node Assignment on an H2O Model
#'
#' Obtains leaf node assignment from fitted H2O model objects.
#'
#' For every row in the test set, return the leaf placements of the row in all the trees in the model.
#' Placements can be represented either by paths to the leaf nodes from the tree root or by H2O's internal identifiers.
#' The order of the rows in the results is the same as the order in which the
#' data was loaded
#'
#' @param object a fitted \linkS4class{H2OModel} object for which prediction is
#'        desired
#' @param newdata An H2OFrame object in which to look for
#'        variables with which to predict.
#' @param type choice of either "Path" when tree paths are to be returned (default); or "Node_ID" when the output
#         should be the leaf node IDs.
#' @param ... additional arguments to pass on.
#' @return Returns an H2OFrame object with categorical leaf assignment identifiers for
#'         each tree in the model.
#' @seealso \code{\link{h2o.gbm}} and  \code{\link{h2o.randomForest}} for model
#'          generation in h2o.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' prostate$CAPSULE <- as.factor(prostate$CAPSULE)
#' prostate_gbm <- h2o.gbm(3:9, "CAPSULE", prostate)
#' h2o.predict(prostate_gbm, prostate)
#' h2o.predict_leaf_node_assignment(prostate_gbm, prostate)
#' }
#' @export
predict_leaf_node_assignment.H2OModel <- function(object, newdata, type = c("Path", "Node_ID"), ...) {
  if (missing(newdata)) {
    stop("predictions with a missing `newdata` argument is not implemented yet")
  }
  params <- list(leaf_node_assignment = TRUE)
  if (!missing(type)) {
    if (!(type %in% c("Path", "Node_ID"))) {
      stop("type must be one of: Path, Node_ID")
    }
    params$leaf_node_assignment_type <- type
  }

  url <- paste0('Predictions/models/', object@model_id, '/frames/',  h2o.getId(newdata))
  res <- .h2o.__remoteSend(url, method = "POST", .params = params)
  res <- res$predictions_frame
  h2o.getFrame(res$name)
}

#' @rdname predict_leaf_node_assignment.H2OModel
#' @export
h2o.predict_leaf_node_assignment <- predict_leaf_node_assignment.H2OModel

#' Use GRLM to transform a frame.
#'
#' @param model H2O GRLM model
#' @param fr H2OFrame
#' @return Returns a transformed frame
#'
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the USArrests dataset into H2O:
#' arrests <- h2o.importFile(
#'   "https://s3.amazonaws.com/h2o-public-test-data/smalldata/pca_test/USArrests.csv"
#' )
#'
#' # Split the dataset into a train and valid set:
#' arrests_splits <- h2o.splitFrame(data = arrests, ratios = 0.8, seed = 1234)
#' train <- arrests_splits[[1]]
#' valid <- arrests_splits[[2]]
#'
#' # Build and train the model:
#' glrm_model = h2o.glrm(training_frame = train,
#'                       k = 4,
#'                       loss = "Quadratic",
#'                       gamma_x = 0.5,
#'                       gamma_y = 0.5,
#'                       max_iterations = 700,
#'                       recover_svd = TRUE,
#'                       init = "SVD",
#'                       transform = "STANDARDIZE")
#'
#' # Eval performance:
#' arrests_perf <- h2o.performance(glrm_model)
#'
#' # Generate predictions on a validation set (if necessary):
#' arrests_pred <- h2o.predict(glrm_model, newdata = valid)
#'
#' # Transform the data using the dataset "valid" to retrieve the new coefficients:
#' glrm_transform <- h2o.transform_frame(glrm_model, valid)
#'}
#' @export
h2o.transform_frame <- function(model, fr) {
  if (!is(model, "H2OModel") || (is(model, "H2OModel") && model@algorithm != "glrm")) stop("h2o.transform_frame can only be applied to GLRM H2OModel instance.")
  return(.newExpr("transform", model@model_id, h2o.getId(fr)))
}

#' Retrieve the results to view the best predictor subsets.
#'
#' @param model H2OModelSelection  object
#' @return Returns an H2OFrame object
#'
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' # Import the prostate dataset:
#' prostate <- h2o.importFile(
#'    "http://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv"
#' )
#'
#' # Set the predictors & response:
#' predictors <- c("AGE", "RACE", "CAPSULE", "DCAPS", "PSA", "VOL", "DPROS")
#' response <- "GLEASON"
#'
#' # Build & train the model:
#' allsubsetsModel <- h2o.modelSelection(x = predictors,
#'                                       y = response,
#'                                       training_frame = prostate,
#'                                       seed = 12345,
#'                                       max_predictor_number = 7,
#'                                       mode = "allsubsets")
#'
#' # Retrieve the results (H2OFrame containing best model_ids, best_r2_value, & predictor subsets):
#' results <- h2o.result(allsubsetsModel)
#' print(results)
#'
#' # Retrieve the list of coefficients:
#' coeff <- h2o.coef(allsubsetsModel)
#' print(coeff)
#'
#' # Retrieve the list of coefficients for a subset size of 3:
#' coeff3 <- h2o.coef(allsubsetsModel, 3)
#' print(coeff3)
#'
#' # Retrieve the list of standardized coefficients:
#' coeff_norm <- h2o.coef_norm(allsubsetsModel)
#' print(coeff_norm)
#'
#' # Retrieve the list of standardized coefficients for a subset size of 3:
#' coeff_norm3 <- h2o.coef_norm(allsubsetsModel)
#' print(coeff_norm3)
#'
#' # Check the variables that were added during this process:
#' h2o.get_predictors_added_per_step(allsubsetsModel)
#'
#' # To find out which variables get removed, build a new model with ``mode = "backward``
#' # using the above training information:
#' bwModel <- h2o.modelSelection(x = predictors,
#'                               y = response,
#'                               training_frame = prostate,
#'                               seed = 12345,
#'                               max_predictor_number = 7,
#'                               mode = "backward")
#' h2o.get_predictors_removed_per_step(bwModel)
#'
#' # To build the fastest model with ModelSelection, use ``mode = "maxrsweep"``:
#' sweepModel <- h2o.modelSelection(x = predictors,
#'                                  y = response,
#'                                  training_frame = prostate,
#'                                  mode = "maxrsweep",
#'                                  build_glm_model = FALSE,
#'                                  max_predictor_number = 3,
#'                                  seed = 12345)
#'
#' # Retrieve the results to view the best predictor subsets:
#' h2o.result(sweepModel)
#' }
#'
#' @export
h2o.result <- function(model) {
  if (!is(model, "H2OModel")) stop("h2o.result can only be applied to H2OModel instances with constant results")
  return(.newExpr("result", model@model_id))
}

h2o.crossValidate <- function(model, nfolds, model.type = c("gbm", "glm", "deeplearning"), params, strategy = c("mod1", "random")) {
  output <- data.frame()

  if( nfolds < 2 ) stop("`nfolds` must be greater than or equal to 2")
  if( missing(model) & missing(model.type) ) stop("must declare `model` or `model.type`")
  else if( missing(model) )
  {
    if(model.type == "gbm") model.type = "h2o.gbm"
    else if(model.type == "glm") model.type = "h2o.glm"
    else if(model.type == "deeplearning") model.type = "h2o.deeplearning"

    model <- do.call(model.type, c(params))
  }
  output[1, "fold_num"] <- -1
  output[1, "model_key"] <- model@model_id
  # output[1, "model"] <- model@model$mse_valid

  data <- params$training_frame
  data <- eval(data)
  data.len <- nrow(data)

  # nfold_vec <- h2o.sample(fr, 1:nfolds)
  nfold_vec <- sample(rep(1:nfolds, length.out = data.len), data.len)

  fnum_id <- as.h2o(nfold_vec)
  fnum_id <- h2o.cbind(fnum_id, data)

  xval <- lapply(1:nfolds, function(i) {
      params$training_frame   <- data[fnum_id[,1] != i, ]
      params$validation_frame <- data[fnum_id[,1] == i, ]
      fold <- do.call(model.type, c(params))
      output[(i+1), "fold_num"] <<- i - 1
      output[(i+1), "model_key"] <<- fold@model_id
      # output[(i+1), "cv_err"] <<- mean(as.vector(fold@model$mse_valid))
      fold
    })

  model
}

#' Predict class probabilities at each stage of an H2O Model
#'
#' The output structure is analogous to the output of \link{h2o.predict_leaf_node_assignment}. For each tree t and
#' class c there will be a column Tt.Cc (eg. T3.C1 for tree 3 and class 1). The value will be the corresponding
#' predicted probability of this class by combining the raw contributions of trees T1.Cc,..,TtCc. Binomial models build
#' the trees just for the first class and values in columns Tx.C1 thus correspond to the the probability p0.
#'
#' @param object a fitted \linkS4class{H2OModel} object for which prediction is
#'        desired
#' @param newdata An H2OFrame object in which to look for
#'        variables with which to predict.
#' @param ... additional arguments to pass on.
#' @return Returns an H2OFrame object with predicted probability for each tree in the model.
#' @seealso \code{\link{h2o.gbm}} and  \code{\link{h2o.randomForest}} for model
#'          generation in h2o.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' prostate$CAPSULE <- as.factor(prostate$CAPSULE)
#' prostate_gbm <- h2o.gbm(3:9, "CAPSULE", prostate)
#' h2o.predict(prostate_gbm, prostate)
#' h2o.staged_predict_proba(prostate_gbm, prostate)
#' }
#' @export
staged_predict_proba.H2OModel <- function(object, newdata, ...) {
  if (missing(newdata)) {
    stop("predictions with a missing `newdata` argument is not implemented yet")
  }

  url <- paste0('Predictions/models/', object@model_id, '/frames/',  h2o.getId(newdata))
  res <- .h2o.__remoteSend(url, method = "POST", predict_staged_proba=TRUE)
  res <- res$predictions_frame
  h2o.getFrame(res$name)
}

#' @rdname staged_predict_proba.H2OModel
#' @export
h2o.staged_predict_proba <- staged_predict_proba.H2OModel

#' Predict feature contributions - SHAP values on an H2O Model (only DRF, GBM, XGBoost models and equivalent imported MOJOs).
#'
#' Default implemntation return H2OFrame shape (#rows, #features + 1) - there is a feature contribution column for each input
#' feature, the last column is the model bias (same value for each row). The sum of the feature contributions
#' and the bias term is equal to the raw prediction of the model. Raw prediction of tree-based model is the sum
#' of the predictions of the individual trees before the inverse link function is applied to get the actual
#' prediction. For Gaussian distribution the sum of the contributions is equal to the model prediction.
#'
#' Note: Multinomial classification models are currently not supported.
#'
#' @param object a fitted \linkS4class{H2OModel} object for which prediction is
#'        desired
#' @param newdata An H2OFrame object in which to look for
#'        variables with which to predict.
#' @param output_format Specify how to output feature contributions in XGBoost - XGBoost by default outputs
#'                      contributions for 1-hot encoded features, specifying a compact output format will produce
#'                      a per-feature contribution. Defaults to original.
#' @param top_n Return only #top_n highest contributions + bias
#'              If top_n<0 then sort all SHAP values in descending order
#'              If top_n<0 && bottom_n<0 then sort all SHAP values in descending order
#' @param bottom_n Return only #bottom_n lowest contributions + bias
#'                 If top_n and bottom_n are defined together then return array of #top_n + #bottom_n + bias
#'                 If bottom_n<0 then sort all SHAP values in ascending order
#'                 If top_n<0 && bottom_n<0 then sort all SHAP values in descending order
#' @param compare_abs True to compare absolute values of contributions
#' @param background_frame Optional frame, that is used as the source of baselines for
#'                         the baseline SHAP (when output_per_reference == TRUE) or for
#'                         the marginal SHAP (when output_per_reference == FALSE).
#' @param output_space If TRUE, linearly scale the contributions so that they sum up to the prediction.
#'                     NOTE: This will result only in approximate SHAP values even if the model supports exact SHAP calculation.
#'                     NOTE: This will not have any effect if the estimator doesn't use a link function.
#' @param output_per_reference If TRUE, return baseline SHAP, i.e., contribution for each data point for each reference from the background_frame.
#'                             If FALSE, return TreeSHAP if no background_frame is provided, or marginal SHAP if background frame is provided.
#'                             Can be used only with background_frame.
#' @param ... additional arguments to pass on.
#' @return Returns an H2OFrame contain feature contributions for each input row.
#' @seealso \code{\link{h2o.gbm}} and  \code{\link{h2o.randomForest}} for model
#'          generation in h2o.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' prostate_gbm <- h2o.gbm(3:9, "AGE", prostate)
#' h2o.predict(prostate_gbm, prostate)
#' # Compute SHAP
#' h2o.predict_contributions(prostate_gbm, prostate)
#' # Compute SHAP and pick the top two highest
#' h2o.predict_contributions(prostate_gbm, prostate, top_n=2)
#' # Compute SHAP and pick the top two lowest
#' h2o.predict_contributions(prostate_gbm, prostate, bottom_n=2)
#' # Compute SHAP and pick the top two highest regardless of the sign
#' h2o.predict_contributions(prostate_gbm, prostate, top_n=2, compare_abs=TRUE)
#' # Compute SHAP and pick the top two lowest regardless of the sign
#' h2o.predict_contributions(prostate_gbm, prostate, bottom_n=2, compare_abs=TRUE)
#' # Compute SHAP values and show them all in descending order
#' h2o.predict_contributions(prostate_gbm, prostate, top_n=-1)
#' # Compute SHAP and pick the top two highest and top two lowest
#' h2o.predict_contributions(prostate_gbm, prostate, top_n=2, bottom_n=2)
#' 
#' # Compute Marginal SHAP, this enables looking at the contributions against different
#' # baselines, e.g., older people in the following example
#' h2o.predict_contributions(prostate_gbm, prostate, background_frame=prostate[prostate$AGE > 75, ])
#' }
#' @export
predict_contributions.H2OModel <- function(object, newdata, output_format = c("compact", "original"), top_n=0, bottom_n=0, compare_abs=FALSE,
                                           background_frame = NULL, output_space = FALSE, output_per_reference = FALSE, ...) {
    if (missing(newdata)) {
        stop("predictions with a missing `newdata` argument is not implemented yet")
    }
    .check_model_suitability_for_calculation_of_contributions(object, background_frame)
    params <- list(predict_contributions = TRUE, top_n=top_n, bottom_n=bottom_n, compare_abs=compare_abs,
         background_frame=if (is.H2OFrame(background_frame)) h2o.getId(background_frame) else NULL,
         output_space=output_space, output_per_reference=output_per_reference
    )
    params$predict_contributions_output_format <- match.arg(output_format)
    url <- paste0('Predictions/models/', object@model_id, '/frames/',  h2o.getId(newdata))
    res <- .h2o.__remoteSend(url, method = "POST", .params = params, h2oRestApiVersion = 4)
    job_key <- res$key$name
    dest_key <- res$dest$name
    .h2o.__waitOnJob(job_key)
    h2o.getFrame(dest_key)
}

#' @rdname predict_contributions.H2OModel
#' @export
h2o.predict_contributions <- predict_contributions.H2OModel

#' Output row to tree assignment for the model and provided training data.
#'
#' Output is frame of size nrow = nrow(original_training_data) and ncol = number_of_trees_in_model+1 in format: 
#'     row_id    tree_1    tree_2    tree_3
#'          0         0         1         1
#'          1         1         1         1
#'          2         1         0         0
#'          3         1         1         0
#'          4         0         1         1
#'          5         1         1         1
#'          6         1         0         0
#'          7         0         1         0
#'          8         0         1         1
#'          9         1         0         0
#' 
#' Where 1 in the tree_\{number\} cols means row is used in the tree and 0 means that row is not used.
#' The structure of the output depends on sample_rate or sample_size parameter setup.
#'
#' Note: Multinomial classification generate tree for each category, each tree use the same sample of the data.
#'
#' @param object a fitted \linkS4class{H2OModel} object
#' @param original_training_data An H2OFrame object that was used for model training. Currently there is no validation of the input.
#' @param ... additional arguments to pass on.
#' @return Returns an H2OFrame contain row to tree assignment for each tree and row.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' prostate_gbm <- h2o.gbm(4:9, "AGE", prostate, sample_rate = 0.6)
#' # Get row to tree assignment
#' h2o.row_to_tree_assignment(prostate_gbm, prostate)
#' }
#' @export
row_to_tree_assignment.H2OModel <- function(object, original_training_data, ...) {
    if (missing(original_training_data)) {
        stop("row_to_tree_assignment with a missing `original_training_data` argument is not implemented yet")
    }
    params <- list(row_to_tree_assignment = TRUE)
    url <- paste0('Predictions/models/', object@model_id, '/frames/',  h2o.getId(original_training_data))
    res <- .h2o.__remoteSend(url, method = "POST", .params = params, h2oRestApiVersion = 4)
    job_key <- res$key$name
    dest_key <- res$dest$name
    .h2o.__waitOnJob(job_key)
    h2o.getFrame(dest_key)
}

#' @rdname row_to_tree_assignment.H2OModel
#' @export
h2o.row_to_tree_assignment <- row_to_tree_assignment.H2OModel


#' Retrieve the number of occurrences of each feature for given observations 
#  on their respective paths in a tree ensemble model.
#' Available for GBM, Random Forest and Isolation Forest models.
#'
#' @param object a fitted \linkS4class{H2OModel} object for which prediction is
#'        desired
#' @param newdata An H2OFrame object in which to look for
#'        variables with which to predict.
#' @param ... additional arguments to pass on.
#' @return Returns an H2OFrame contain per-feature frequencies on the predict path for each input row.
#' @seealso \code{\link{h2o.gbm}} and  \code{\link{h2o.randomForest}} for model
#'          generation in h2o.
feature_frequencies.H2OModel <- function(object, newdata, ...) {
    if (missing(newdata)) {
        stop("predictions with a missing `newdata` argument is not implemented yet")
    }

    url <- paste0('Predictions/models/', object@model_id, '/frames/',  h2o.getId(newdata))
    res <- .h2o.__remoteSend(url, method = "POST", feature_frequencies=TRUE)
    res <- res$predictions_frame
    h2o.getFrame(res$name)
}

#' @rdname feature_frequencies.H2OModel
#' @export
h2o.feature_frequencies <- feature_frequencies.H2OModel


#' Model Performance Metrics in H2O
#'
#' Given a trained h2o model, compute its performance on the given
#' dataset.  However, if the dataset does not contain the response/target column, no performance will be returned.
#' Instead, a warning message will be printed.
#'
#'
#' @param model An \linkS4class{H2OModel} object
#' @param newdata An H2OFrame. The model will make predictions
#'        on this dataset, and subsequently score them. The dataset should
#'        match the dataset that was used to train the model, in terms of
#'        column names, types, and dimensions. If newdata is passed in, then train, valid, and xval are ignored.
#' @param train A logical value indicating whether to return the training metrics (constructed during training).
#'
#' Note: when the trained h2o model uses balance_classes, the training metrics constructed during training will be from the balanced training dataset.
#' For more information visit: \url{https://github.com/h2oai/h2o-3/discussions/15518}
#' @param valid A logical value indicating whether to return the validation metrics (constructed during training).
#' @param xval A logical value indicating whether to return the cross-validation metrics (constructed during training).
#' @param data (DEPRECATED) An H2OFrame. This argument is now called `newdata`.
#' @param auc_type For multinomila model only. Set default multinomial AUC type. Must be one of: "AUTO", "NONE", "MACRO_OVR", "WEIGHTED_OVR", "MACRO_OVO",
#'        "WEIGHTED_OVO". Default is "NONE"
#' @param auuc_type For binomial model only. Set default AUUC type. Must be one of: "AUTO", "GINI", "GAIN", "LIFT". Default is NULL.
#' @return Returns an object of the \linkS4class{H2OModelMetrics} subclass.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' prostate$CAPSULE <- as.factor(prostate$CAPSULE)
#' prostate_gbm <- h2o.gbm(3:9, "CAPSULE", prostate)
#' h2o.performance(model = prostate_gbm, newdata=prostate)
#'
#' ## If model uses balance_classes
#' ## the results from train = TRUE will not match the results from newdata = prostate
#' prostate_gbm_balanced <- h2o.gbm(3:9, "CAPSULE", prostate, balance_classes = TRUE)
#' h2o.performance(model = prostate_gbm_balanced, newdata = prostate)
#' h2o.performance(model = prostate_gbm_balanced, train = TRUE)
#' }
#' @export
h2o.performance <- function(model, newdata=NULL, train=FALSE, valid=FALSE, xval=FALSE, data=NULL, auc_type="NONE", auuc_type=NULL) {

  # data is now deprecated and the new arg name is newdata
  if (!is.null(data)) {
    warning("The `data` argument is DEPRECATED; use `newdata` instead as `data` will eventually be removed")
    if (is.null(newdata)) newdata <- data
    else stop("Do not use both `data` and `newdata`; just use `newdata`")
  }

  # Some parameter checking
  if(!is(model, "H2OModel")) stop("`model` must an H2OModel object")
  if(!is.null(newdata) && !is.H2OFrame(newdata)) stop("`newdata` must be an H2OFrame object")
  if(!is.logical(train) || length(train) != 1L || is.na(train)) stop("`train` must be TRUE or FALSE")
  if(!is.logical(valid) || length(valid) != 1L || is.na(valid)) stop("`valid` must be TRUE or FALSE")
  if(!is.logical(xval) || length(xval) != 1L || is.na(xval)) stop("`xval` must be TRUE or FALSE")
  if(sum(valid, xval, train) > 1) stop("only one of `train`, `valid`, and `xval` can be TRUE")
  if(!(auc_type %in% c("AUTO", "NONE", "MACRO_OVR", "WEIGHTED_OVR", "MACRO_OVO", "WEIGHTED_OVO"))) stop("`auc_type` must be \"AUTO\", \"NONE\", \"MACRO_OVR\", \"WEIGHTED_OVR\", \"MACRO_OVO\", or \"WEIGHTED_OVO\".")
  if(!is.null(auuc_type) && !(auuc_type %in% c("AUTO", "GINI", "LIFT", "GAIN"))) stop("`auuc_type` must be \"AUTO\", \"GINI\", \"LIFT\" or \"GAIN\"." )
    
  missingNewdata <- missing(newdata) || is.null(newdata)
  if( missingNewdata && auc_type != "NONE") {
    print("WARNING: The `auc_type` parameter is set but it is not used because the `newdata` parameter is NULL.")
  }
  if( missingNewdata && !is.null(auuc_type)) {
    print("WARNING: The `auuc_type` parameter is set but it is not used because the `newdata` parameter is NULL.")
  }  
  if( !missingNewdata ) {
    if (!is.null(model@parameters$y)  &&  !(model@parameters$y %in% names(newdata))) {
      print("WARNING: Model metrics cannot be calculated and metric_json is empty due to the absence of the response column in your dataset.")
      return(NULL)
    }
    newdata.id <- h2o.getId(newdata)
    parms <- list()
    parms[["model"]] <- model@model_id
    parms[["frame"]] <- newdata.id
    if(auc_type != "NONE"){
        parms[["auc_type"]] <- auc_type 
    } else if(!is.null(model@parameters$auc_type) && model@parameters$auc_type != "NONE"){
        parms[["auc_type"]] <- model@parameters$auc_type
    }
    if(!is.null(auuc_type)){
        parms[["auuc_type"]] <- auuc_type
    } else if(!is.null(model@parameters$auuc_type)){
        parms[["auuc_type"]] <- model@parameters$auuc_type
    }
    res <- .h2o.__remoteSend(method = "POST", .h2o.__MODEL_METRICS(model@model_id, newdata.id), .params = parms)

    ####
    model_metrics <- Filter(function(mm) { mm$frame$name==newdata.id}, res$model_metrics)[[1]]   # filter on newdata.id, R's builtin Filter function
    #
    ####
    metrics <- model_metrics[!(names(model_metrics) %in% c("__meta", "names", "domains", "model_category"))]
    model_category <- model_metrics$model_category
    Class <- paste0("H2O", model_category, "Metrics")
    metrics$frame <- list()
    metrics$frame$name <- newdata.id
    new(Class     = Class,
        algorithm = model@algorithm,
        on_train  = missingNewdata,
        metrics   = metrics)
  }
  else if( train || (!train && !valid && !xval) ) return(model@model$training_metrics)    # no newdata, train, valid, and xval are false (all defaults), return the training metrics
  else if( valid ) {
    if( is.null(model@model$validation_metrics@metrics) ) return(NULL) # no newdata, but valid is true, return the validation metrics
    else                                                  return(model@model$validation_metrics)
  }
  else { #if xval
    if( is.null(model@model$cross_validation_metrics@metrics) ) return(NULL) # no newdata, but xval is true, return the crosss_validation metrics
    else                                                        return(model@model$cross_validation_metrics)
  }
}

#' Create Model Metrics from predicted and actual values in H2O
#'
#' Given predicted values (target for regression, class-1 probabilities or binomial
#' or per-class probabilities for multinomial), compute a model metrics object
#'
#' @param predicted An H2OFrame containing predictions
#' @param actuals An H2OFrame containing actual values
#' @param domain Vector with response factors for classification.
#' @param distribution Distribution for regression.
#' @param weights (optional) An H2OFrame containing observation weights.
#' @param treatment (optional, for uplift models only) An H2OFrame containing treatment column for uplift classification.
#' @param auc_type (optional) For multinomial classification you have to specify which type of agregated AUC/AUCPR will be used to calculate this metric. 
#         Possibilities are MACRO_OVO, MACRO_OVR, WEIGHTED_OVO, WEIGHTED_OVR (OVO = One vs. One, OVR = One vs. Rest)
#' @param auuc_type (optional) For uplift binomial classification you have to specify which type of AUUC will be used to 
#'        calculate this metric. Possibilities are gini, lift, gain, AUTO. Default is AUTO which means qini.
#' @param auuc_nbins (optional) For uplift binomial classification you can specify number of bins to be used 
#'        for calculation the AUUC. Default is -1, which means 1000.
#' @param custom_auuc_thresholds (optional) For uplift binomial classification you can specify exact thresholds to 
#'        calculate AUUC. Default is NULL. If the thresholds are not defined, auuc_nbins will be used to calculate 
#'        new thresholds from the predicted data. 
#' @return Returns an object of the \linkS4class{H2OModelMetrics} subclass.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' prostate$CAPSULE <- as.factor(prostate$CAPSULE)
#' prostate_gbm <- h2o.gbm(3:9, "CAPSULE", prostate)
#' pred <- h2o.predict(prostate_gbm, prostate)[, 3] ## class-1 probability
#' h2o.make_metrics(pred, prostate$CAPSULE)
#' }
#' @export
h2o.make_metrics <- function(predicted, actuals, domain=NULL, distribution=NULL, weights=NULL, treatment=NULL, 
                                auc_type="NONE", auuc_type="AUTO", auuc_nbins=-1, custom_auuc_thresholds=NULL) {
  predicted <- .validate.H2OFrame(predicted, required=TRUE)
  actuals <- .validate.H2OFrame(actuals, required=TRUE)
  weights <- .validate.H2OFrame(weights, required=FALSE)
  treatment <- .validate.H2OFrame(treatment, required=FALSE)
  if (!is.character(auc_type)) stop("auc_type argument must be of type character")
  if (!(auc_type %in% c("MACRO_OVO", "MACRO_OVR", "WEIGHTED_OVO", "WEIGHTED_OVR", "NONE", "AUTO"))) {
    stop("auc_type argument must be MACRO_OVO, MACRO_OVR, WEIGHTED_OVO, WEIGHTED_OVR, NONE, AUTO")
  }
  params <- list()
  params$predictions_frame <- h2o.getId(predicted)
  params$actuals_frame <- h2o.getId(actuals)
  if (!is.null(weights)) {
    params$weights_frame <- h2o.getId(weights)
  }
  if (!is.null(treatment)) {
      params$treatment_frame <- h2o.getId(treatment)
      if (!(auuc_type %in% c("qini", "lift", "gain", "AUTO"))) {
        stop("auuc_type argument must be gini, lift, gain or AUTO")
      }
      if (auuc_nbins < -1 || auuc_nbins == 0) {
        stop("auuc_nbins must be -1 or higher than 0.")
      }
      params$auuc_type <- auuc_type
      params$auuc_nbins <- auuc_nbins
      if(!is.null(custom_auuc_thresholds)){ 
         params$custom_auuc_thresholds <- paste("[", paste(custom_auuc_thresholds, collapse = ", "),"]")
      }
  }
  params$domain <- domain
  params$distribution <- distribution

  if (is.null(domain) && !is.null(h2o.levels(actuals)))
    domain <- h2o.levels(actuals)

  ## pythonify the domain
  if (!is.null(domain)) {
    out <- paste0('["',domain[1],'"')
    for (d in 2:length(domain)) {
      out <- paste0(out,',"',domain[d],'"')
    }
    out <- paste0(out, "]")
    params[["domain"]] <- out
  }
  params["auc_type"] <- auc_type  
  url <- paste0("ModelMetrics/predictions_frame/",params$predictions_frame,"/actuals_frame/",params$actuals_frame,"/treatment_frame/",params$treatment_frame)
  res <- .h2o.__remoteSend(method = "POST", url, .params = params)
  model_metrics <- res$model_metrics
  metrics <- model_metrics[!(names(model_metrics) %in% c("__meta", "names", "domains", "model_category"))]
  name <- "H2ORegressionMetrics"
  if (!is.null(metrics$auuc_table)) name <- "H2OBinomialUpliftMetrics"
  else if (!is.null(metrics$AUC) && is.null(metrics$hit_ratio_table)) name <- "H2OBinomialMetrics"
  else if (!is.null(distribution) && distribution == "ordinal") name <- "H2OOrdinalMetrics"
  else if (!is.null(metrics$hit_ratio_table)) name <- "H2OMultinomialMetrics"
  new(Class = name, metrics = metrics)
}

#' Retrieve the AUC
#'
#' Retrieves the AUC value from an \linkS4class{H2OBinomialMetrics}.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training AUC value is returned. If more
#' than one parameter is set to TRUE, then a named vector of AUCs are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OBinomialMetrics} or \linkS4class{H2OMultinomialMetrics} object.
#' @param train Retrieve the training AUC
#' @param valid Retrieve the validation AUC
#' @param xval Retrieve the cross-validation AUC
#' @seealso \code{\link{h2o.giniCoef}} for the Gini coefficient,
#'          \code{\link{h2o.mse}} for MSE, and \code{\link{h2o.metric}} for the
#'          various threshold metrics. See \code{\link{h2o.performance}} for
#'          creating H2OModelMetrics objects.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#'
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' perf <- h2o.performance(model, prostate)
#' h2o.auc(perf)
#' }
#' @export
h2o.auc <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$AUC )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$AUC
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$AUC)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$AUC)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$AUC)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No AUC for ", class(object)))
  invisible(NULL)
}

#' Retrieve AUUC
#'
#' Retrieves the AUUC value from an \linkS4class{H2OBinomialUpliftMetrics}. If the metric parameter is "AUTO", 
#' the type of AUUC depends on auuc_type which was set before training. If you need specific AUUC, set metric parameter.
#' If "train" and "valid" parameters are FALSE (default), then the training AUUC value is returned. If more
#' than one parameter is set to TRUE, then a named vector of AUUCs are returned, where the names are "train", "valid".
#'
#' @param object An \linkS4class{H2OBinomialUpliftMetrics}
#' @param train Retrieve the training AUUC
#' @param valid Retrieve the validation AUUC
#' @param metric Specify the AUUC metric to get specific AUUC. Possibilities are NULL, "qini", "lift", "gain".
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv"
#' train <- h2o.importFile(f)
#' train$treatment <- as.factor(train$treatment)
#' train$conversion <- as.factor(train$conversion)
#' 
#' model <- h2o.upliftRandomForest(training_frame=train, x=sprintf("f%s",seq(0:10)), y="conversion",
#'                                 ntrees=10, max_depth=5, treatment_column="treatment", 
#'                                 auuc_type="AUTO")
#' perf <- h2o.performance(model, train=TRUE) 
#' h2o.auuc(perf)
#' }
#' @export
h2o.auuc <- function(object, train=FALSE, valid=FALSE, metric=NULL) {
    if(!is.null(metric) && !metric %in% c("qini", "lift", "gain")) 
        stop("metric must be NULL, 'qini', 'lift' or 'gain'")
    if( is(object, "H2OModelMetrics") ) {
        if(is.null(metric)) { 
            return( object@metrics$AUUC )
        } else {
            return( eval(parse(text=paste("object@metrics$auuc_table$", metric,"[1]", sep=""))))
        }
    }
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid ) {
            if (is.null(metric)) { 
                mm <- model.parts$tm@metrics$AUUC
            } else {
                mm <- eval(parse(text=paste("model.parts$tm@metrics$auuc_table$", metric,"[1]", sep="")))
            }
            if ( !is.null(mm) ) return(mm)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            if (is.null(metric)) { 
                mm <- model.parts$tm@metrics$AUUC
            } else {
                mm <- eval(parse(text=paste("model.parts$tm@metrics$auuc_table$", metric,"[1]", sep="")))
            }
            v <- c(v, mm)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                if (is.null(metric)) { 
                    mm <- model.parts$vm@metrics$AUUC
                } else {
                    mm <- eval(parse(text=paste("model.parts$vm@metrics$auuc_table$", metric,"[1]", sep="")))
                }
                v <- c(v, mm)
                v_names <- c(v_names,"valid")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No AUUC for ", class(object)))
    invisible(NULL)
}

#' Retrieve Average Treatment Effect
#'
#' Retrieves ATE from an \linkS4class{H2OBinomialUpliftMetrics}.
#' If "train" and "valid" parameters are FALSE (default), then the training ATE is returned. If more
#' than one parameter is set to TRUE, then a named vector of ATE values are returned, where the names are "train", "valid".
#'
#' @param object An \linkS4class{H2OBinomialUpliftMetrics} or 
#' @param train Retrieve the training ATE value
#' @param valid Retrieve the validation ATE value
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv"
#' train <- h2o.importFile(f)
#' train$treatment <- as.factor(train$treatment)
#' train$conversion <- as.factor(train$conversion)
#' 
#' model <- h2o.upliftRandomForest(training_frame=train, x=sprintf("f%s",seq(0:10)), y="conversion",
#'                                 ntrees=10, max_depth=5, treatment_column="treatment",
#'                                 auuc_type="AUTO")
#' perf <- h2o.performance(model, train=TRUE) 
#' h2o.ate(perf)
#' }
#' @export
h2o.ate <- function(object, train=FALSE, valid=FALSE) {
    if( is(object, "H2OModelMetrics") ) return( object@metrics$ate )
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid ) {
            metric <- model.parts$tm@metrics$ate
            if ( !is.null(metric) ) return(metric)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            v <- c(v,model.parts$tm@metrics$ate)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                v <- c(v,model.parts$vm@metrics$ate)
                v_names <- c(v_names,"valid")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No ATE value for ", class(object)))
    invisible(NULL)
}

#' Retrieve Average Treatment Effect on the Treated
#'
#' Retrieves ATE from an \linkS4class{H2OBinomialUpliftMetrics}.
#' If "train" and "valid" parameters are FALSE (default), then the training ATT is returned. If more
#' than one parameter is set to TRUE, then a named vector of ATT values are returned, where the names are "train", "valid".
#'
#' @param object An \linkS4class{H2OBinomialUpliftMetrics} or 
#' @param train Retrieve the training ATT value
#' @param valid Retrieve the validation ATT value
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv"
#' train <- h2o.importFile(f)
#' train$treatment <- as.factor(train$treatment)
#' train$conversion <- as.factor(train$conversion)
#' 
#' model <- h2o.upliftRandomForest(training_frame=train, x=sprintf("f%s",seq(0:10)), y="conversion",
#'                                 ntrees=10, max_depth=5, treatment_column="treatment",
#'                                 auuc_type="AUTO")
#' perf <- h2o.performance(model, train=TRUE) 
#' h2o.att(perf)
#' }
#' @export
h2o.att <- function(object, train=FALSE, valid=FALSE) {
    if( is(object, "H2OModelMetrics") ) return( object@metrics$att )
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid ) {
            metric <- model.parts$tm@metrics$att
            if ( !is.null(metric) ) return(metric)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            v <- c(v,model.parts$tm@metrics$att)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                v <- c(v,model.parts$vm@metrics$att)
                v_names <- c(v_names,"valid")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No ATT value for ", class(object)))
    invisible(NULL)
}

#' Retrieve Average Treatment Effect on the Control
#'
#' Retrieves ATC from an \linkS4class{H2OBinomialUpliftMetrics}.
#' If "train" and "valid" parameters are FALSE (default), then the training ATC is returned. If more
#' than one parameter is set to TRUE, then a named vector of ATC values are returned, where the names are "train", "valid".
#'
#' @param object An \linkS4class{H2OBinomialUpliftMetrics} or 
#' @param train Retrieve the training ATC value
#' @param valid Retrieve the validation ATC value
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv"
#' train <- h2o.importFile(f)
#' train$treatment <- as.factor(train$treatment)
#' train$conversion <- as.factor(train$conversion)
#' 
#' model <- h2o.upliftRandomForest(training_frame=train, x=sprintf("f%s",seq(0:10)), y="conversion",
#'                                 ntrees=10, max_depth=5, treatment_column="treatment",
#'                                 auuc_type="AUTO")
#' perf <- h2o.performance(model, train=TRUE) 
#' h2o.atc(perf)
#' }
#' @export
h2o.atc <- function(object, train=FALSE, valid=FALSE) {
    if( is(object, "H2OModelMetrics") ) return( object@metrics$atc )
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid ) {
            metric <- model.parts$tm@metrics$atc
            if ( !is.null(metric) ) return(metric)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            v <- c(v,model.parts$tm@metrics$atc)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                v <- c(v,model.parts$vm@metrics$atc)
                v_names <- c(v_names,"valid")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No ATC value for ", class(object)))
    invisible(NULL)
}

#' Retrieve normalized AUUC
#'
#' Retrieves the AUUC value from an \linkS4class{H2OBinomialUpliftMetrics}. If the metric parameter is "AUTO", 
#' the type of AUUC depends on auuc_type which was set before training. If you need specific normalized AUUC, 
#' set metric parameter. If "train" and "valid" parameters are FALSE (default), then the training normalized AUUC 
#' value is returned. If more than one parameter is set to TRUE, then a named vector of normalized AUUCs are returned, 
#' where the names are "train", "valid".
#'
#' @param object An \linkS4class{H2OBinomialUpliftMetrics}
#' @param train Retrieve the training AUUC
#' @param valid Retrieve the validation AUUC
#' @param metric Specify the AUUC metric to get specific AUUC. Possibilities are NULL, "qini", "lift", "gain".
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv"
#' train <- h2o.importFile(f)
#' train$treatment <- as.factor(train$treatment)
#' train$conversion <- as.factor(train$conversion)
#' 
#' model <- h2o.upliftRandomForest(training_frame=train, x=sprintf("f%s",seq(0:10)), y="conversion",
#'                                 ntrees=10, max_depth=5, treatment_column="treatment", 
#'                                 auuc_type="AUTO")
#' perf <- h2o.performance(model, train=TRUE) 
#' h2o.auuc_normalized(perf)
#' }
#' @export
h2o.auuc_normalized <- function(object, train=FALSE, valid=FALSE, metric=NULL) {
    if(!is.null(metric) && !metric %in% c("qini", "lift", "gain"))
        stop("metric must be NULL, 'qini', 'lift' or 'gain'")
    if( is(object, "H2OModelMetrics") ) {
        if(is.null(metric)) {
            return( object@metrics$auuc_normalized )
        } else {
            return( eval(parse(text=paste("object@metrics$auuc_table$", metric,"[2]", sep=""))))
        }
    }
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid ) {
            if (is.null(metric)) {
                mm <- model.parts$tm@metrics$AUUC
            } else {
                mm <- eval(parse(text=paste("model.parts$tm@metrics$auuc_table$", metric,"[2]", sep="")))
            }
            if ( !is.null(mm) ) return(mm)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            if (is.null(metric)) {
                mm <- model.parts$tm@metrics$AUUC
            } else {
                mm <- eval(parse(text=paste("model.parts$tm@metrics$auuc_table$", metric,"[2]", sep="")))
            }
            v <- c(v, mm)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                if (is.null(metric)) {
                    mm <- model.parts$vm@metrics$AUUC
                } else {
                    mm <- eval(parse(text=paste("model.parts$vm@metrics$auuc_table$", metric,"[2]", sep="")))
                }
                v <- c(v, mm)
                v_names <- c(v_names,"valid")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No AUUC normalized for ", class(object)))
    invisible(NULL)
}

#' Retrieve the all types of AUUC in a table
#'
#' Retrieves the all types of AUUC in a table from an \linkS4class{H2OBinomialUpliftMetrics}.
#' If "train" and "valid" parameters are FALSE (default), then the training AUUC values are returned. If more
#' than one parameter is set to TRUE, then a named vector of AUUCs are returned, where the names are "train", "valid".
#'
#' @param object An \linkS4class{H2OBinomialUpliftMetrics}
#' @param train Retrieve the training AUUC table
#' @param valid Retrieve the validation AUUC table
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv"
#' train <- h2o.importFile(f)
#' train$treatment <- as.factor(train$treatment)
#' train$conversion <- as.factor(train$conversion)
#' 
#' model <- h2o.upliftRandomForest(training_frame=train, x=sprintf("f%s",seq(0:10)), y="conversion",
#'                                        ntrees=10, max_depth=5, treatment_column="treatment", 
#'                                        auuc_type="AUTO")
#' perf <- h2o.performance(model, train=TRUE) 
#' h2o.auuc_table(perf)
#' }
#' @export
h2o.auuc_table <- function(object, train=FALSE, valid=FALSE) {
    if( is(object, "H2OModelMetrics") ) return( object@metrics$auuc_table )
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid ) {
            metric <- model.parts$tm@metrics$auuc_table
            if ( !is.null(metric) ) return(metric)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            v <- c(v,model.parts$tm@metrics$auuc_table)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                v <- c(v,model.parts$vm@metrics$auuc_table)
                v_names <- c(v_names,"valid")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No AUUC table for ", class(object)))
    invisible(NULL)
}

#' Retrieve the thresholds and metric scores table
#'
#' Retrieves the thresholds and metric scores table from a \linkS4class{H2OBinomialUpliftMetrics} 
#' or a \linkS4class{H2OBinomialMetrics}.
#' 
#' The table contains indices, thresholds, all cumulative uplift values and cumulative number of observations for 
#' uplift binomial models or thresholds and maximal metric values for binomial models. 
#' If "train" and "valid" parameters are FALSE (default), then the training table is returned. If more
#' than one parameter is set to TRUE, then a named vector of tables is returned, where the names are "train", "valid".
#'
#' @param object A \linkS4class{H2OBinomialUpliftMetrics} or a \linkS4class{H2OBinomialMetrics}
#' @param train Retrieve the training thresholds and metric scores table
#' @param valid Retrieve the validation thresholds and metric scores table
#' @param xval Retrieve the cross-validation thresholds and metric scores table (only for \linkS4class{H2OBinomialMetrics})
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv"
#' train <- h2o.importFile(f)
#' train$treatment <- as.factor(train$treatment)
#' train$conversion <- as.factor(train$conversion)
#' 
#' model <- h2o.upliftRandomForest(training_frame=train, x=sprintf("f%s",seq(0:10)), y="conversion",
#'                                 ntrees=10, max_depth=5, treatment_column="treatment", 
#'                                 auuc_type="AUTO")
#' perf <- h2o.performance(model, train=TRUE) 
#' h2o.thresholds_and_metric_scores(perf)
#' }
#' @export
h2o.thresholds_and_metric_scores <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
    if( is(object, "H2OModelMetrics") ) return( object@metrics$thresholds_and_metric_score)
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid && !xval) {
            metric <- model.parts$tm@metrics$thresholds_and_metric_score
            if ( !is.null(metric) ) return(metric)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            v <- c(v,model.parts$tm@metrics$thresholds_and_metric_score)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                v <- c(v,model.parts$vm@metrics$thresholds_and_metric_score)
                v_names <- c(v_names,"valid")
            }
        }
        if ( xval ) {
            if( is.null(model.parts$xval) ) return(invisible(.warn.no.cross.validation()))
            else {
                v <- c(v,model.parts$xm@metrics$thresholds_and_metric_score)
                v_names <- c(v_names,"xval")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No thresholds_and_metric_score table for ", class(object)))
    invisible(NULL)
}

#' Retrieve the default Qini value
#'
#' Retrieves the Qini value from an \linkS4class{H2OBinomialUpliftMetrics}.
#' If "train" and "valid" parameters are FALSE (default), then the training Qini value is returned. If more
#' than one parameter is set to TRUE, then a named vector of Qini values are returned, where the names are "train", "valid".
#'
#' @param object An \linkS4class{H2OBinomialUpliftMetrics} or 
#' @param train Retrieve the training Qini value
#' @param valid Retrieve the validation Qini
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv"
#' train <- h2o.importFile(f)
#' train$treatment <- as.factor(train$treatment)
#' train$conversion <- as.factor(train$conversion)
#' 
#' model <- h2o.upliftRandomForest(training_frame=train, x=sprintf("f%s",seq(0:10)), y="conversion",
#'                                 ntrees=10, max_depth=5, treatment_column="treatment",
#'                                 auuc_type="AUTO")
#' perf <- h2o.performance(model, train=TRUE) 
#' h2o.qini(perf)
#' }
#' @export
h2o.qini <- function(object, train=FALSE, valid=FALSE) {
    if( is(object, "H2OModelMetrics") ) return( object@metrics$qini )
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid ) {
            metric <- model.parts$tm@metrics$qini
            if ( !is.null(metric) ) return(metric)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            v <- c(v,model.parts$tm@metrics$qini)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                v <- c(v,model.parts$vm@metrics$qini)
                v_names <- c(v_names,"valid")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No Qini value for ", class(object)))
    invisible(NULL)
}

#' Retrieve the default AECU (Average Excess Cumulative Uplift = area between AUUC and random AUUC)
#'
#' Retrieves the AECU value from an \linkS4class{H2OBinomialUpliftMetrics}. You need to specificy the type of AECU
#' using metric parameter. Defaults "qini". Qini AECU equals the Qini value.
#' If "train" and "valid" parameters are FALSE (default), then the training AECU value is returned. If more
#' than one parameter is set to TRUE, then a named vector of AECUs are returned, where the names are "train", "valid".
#'
#' @param object An \linkS4class{H2OBinomialUpliftMetrics}
#' @param train Retrieve the training AECU
#' @param valid Retrieve the validation AECU
#' @param metric Specify metric of AECU. Posibilities are "qini", "lift", "gain", defaults "qini".
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv"
#' train <- h2o.importFile(f)
#' train$treatment <- as.factor(train$treatment)
#' train$conversion <- as.factor(train$conversion)
#' 
#' model <- h2o.upliftRandomForest(training_frame=train, x=sprintf("f%s",seq(0:10)), y="conversion",
#'                                 ntrees=10, max_depth=5, treatment_column="treatment",
#'                                 auuc_type="AUTO")
#' perf <- h2o.performance(model, train=TRUE) 
#' h2o.aecu(perf)
#' }
#' @export
h2o.aecu <- function(object, train=FALSE, valid=FALSE, metric="qini") {
    if(!metric %in% c("qini", "lift", "gain")) stop("metric must be 'qini', 'lift' or 'gain'")
    if( is(object, "H2OModelMetrics") ) {
        return( eval(parse(text=paste("object@metrics$aecu_table$", metric,"[1]", sep=""))))
    }
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid ) {
            mm <- eval(parse(text=paste("model.parts$tm@metrics$aecu_table$", metric,"[1]", sep="")))
            if ( !is.null(mm) ) return(mm)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            mm <- eval(parse(text=paste("model.parts$tm@metrics$aecu_table$", metric,"[1]", sep="")))
            v <- c(v, mm)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                mm <- eval(parse(text=paste("model.parts$vm@metrics$auuc_table$", metric,"[1]", sep="")))
                v <- c(v, mm)
                v_names <- c(v_names,"valid")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No AECU for ", class(object)))
    invisible(NULL)
}

#' Retrieve the all types of AECU (average excess cumulative uplift) value in a table
#'
#' Retrieves the all types of AECU value in a table from an \linkS4class{H2OBinomialUpliftMetrics}.
#' If "train" and "valid" parameters are FALSE (default), then the training AECU values are returned. If more
#' than one parameter is set to TRUE, then a named vector of AECU values are returned, where the names are "train", "valid".
#'
#' @param object An \linkS4class{H2OBinomialUpliftMetrics}
#' @param train Retrieve the training AECU values table
#' @param valid Retrieve the validation AECU values table
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/uplift/criteo_uplift_13k.csv"
#' train <- h2o.importFile(f)
#' train$treatment <- as.factor(train$treatment)
#' train$conversion <- as.factor(train$conversion)
#' 
#' model <- h2o.upliftRandomForest(training_frame=train, x=sprintf("f%s",seq(0:10)), y="conversion",
#'                                 ntrees=10, max_depth=5, treatment_column="treatment",
#'                                 auuc_type="AUTO")
#' perf <- h2o.performance(model, train=TRUE) 
#' h2o.aecu_table(perf)
#' }
#' @export
h2o.aecu_table <- function(object, train=FALSE, valid=FALSE) {
    if( is(object, "H2OModelMetrics") ) return( object@metrics$aecu_table )
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid ) {
            metric <- model.parts$tm@metrics$aecu_table
            if ( !is.null(metric) ) return(metric)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            v <- c(v,model.parts$tm@metrics$aecu_table)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                v <- c(v,model.parts$vm@metrics$aecu_table)
                v_names <- c(v_names,"valid")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No AECU table for ", class(object)))
    invisible(NULL)
}


#' Internal function that calculates a precise AUC from given
#' probabilities and actual responses.
#' 
#' Note: The underlying implementation is not distributed and can
#' only handle limited size of data. For internal use only.
#' 
#' @param probs An \linkS4class{H2OFrame} holding vector of probabilities.
#' @param acts An \linkS4class{H2OFrame} holding vector of actuals.
.h2o.perfect_auc <- function(probs, acts) {
  .newExpr("perfectAUC", probs, acts)[1, 1]
}

#' Retrieve the all AUC values in a table (One to Rest, One to One, macro and weighted average) 
#' for mutlinomial classification.
#'
#' Retrieves the AUC table from an \linkS4class{H2OMultinomialMetrics}.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training AUC table is returned. If more
#' than one parameter is set to TRUE, then a named vector of AUC tables are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OMultinomialMetrics} object.
#' @param train Retrieve the training AUC table
#' @param valid Retrieve the validation AUC table
#' @param xval Retrieve the cross-validation AUC table
#' @seealso \code{\link{h2o.giniCoef}} for the Gini coefficient,
#'          \code{\link{h2o.mse}} for MSE, and \code{\link{h2o.metric}} for the
#'          various threshold metrics. See \code{\link{h2o.performance}} for
#'          creating H2OModelMetrics objects.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#'
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' perf <- h2o.performance(model, prostate)
#' h2o.multinomial_auc_table(perf)
#' }
#' @export
h2o.multinomial_auc_table <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
    if( is(object, "H2OModelMetrics") ) return( object@metrics$multinomial_auc_table )
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid && !xval ) {
            metric <- model.parts$tm@metrics$multinomial_auc_table
            if ( !is.null(metric) ) return(metric)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            v <- c(v,model.parts$tm@metrics$multinomial_auc_table)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                v <- c(v,model.parts$vm@metrics$multinomial_auc_table)
                v_names <- c(v_names,"valid")
            }
        }
        if ( xval ) {
            if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
            else {
                v <- c(v,model.parts$xm@metrics$multinomial_auc_table)
                v_names <- c(v_names,"xval")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("Multinomial AUC table is not computed because it is disabled (model parameter 'auc_type' is set to AUTO or NONE) or due to domain size (maximum is 50 domains).", class(object)))
    invisible(NULL)
}

#' Retrieve the AUCPR (Area Under Precision Recall Curve)
#'
#' Retrieves the AUCPR value from an \linkS4class{H2OBinomialMetrics}.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training AUCPR value is returned. If more
#' than one parameter is set to TRUE, then a named vector of AUCPRs are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OBinomialMetrics} object.
#' @param train Retrieve the training aucpr
#' @param valid Retrieve the validation aucpr
#' @param xval Retrieve the cross-validation aucpr
#' @seealso \code{\link{h2o.giniCoef}} for the Gini coefficient,
#'          \code{\link{h2o.mse}} for MSE, and \code{\link{h2o.metric}} for the
#'          various threshold metrics. See \code{\link{h2o.performance}} for
#'          creating H2OModelMetrics objects.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#'
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' perf <- h2o.performance(model, prostate)
#' h2o.aucpr(perf)
#' }
#' @export
h2o.aucpr <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$pr_auc )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$pr_auc
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$pr_auc)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$pr_auc)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$pr_auc)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("Multinomial PR AUC table is not computed because it is disabled (model parameter 'auc_type' is set to AUTO or NONE) or due to domain size (maximum is 50 domains).", class(object)))
  invisible(NULL)
}

#' Retrieve the all PR AUC values in a table (One to Rest, One to One, macro and weighted average) 
#' for mutlinomial classification.
#'
#' Retrieves the PR AUC table from an \linkS4class{H2OMultinomialMetrics}.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training PR AUC table is returned. If more
#' than one parameter is set to TRUE, then a named vector of PR AUC tables are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OMultinomialMetrics} object.
#' @param train Retrieve the training PR AUC table
#' @param valid Retrieve the validation PR AUC table
#' @param xval Retrieve the cross-validation PR AUC table
#' @seealso \code{\link{h2o.giniCoef}} for the Gini coefficient,
#'          \code{\link{h2o.mse}} for MSE, and \code{\link{h2o.metric}} for the
#'          various threshold metrics. See \code{\link{h2o.performance}} for
#'          creating H2OModelMetrics objects.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#'
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' perf <- h2o.performance(model, prostate)
#' h2o.multinomial_aucpr_table(perf)
#' }
#' @export
h2o.multinomial_aucpr_table <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
    if( is(object, "H2OModelMetrics") ) return( object@metrics$multinomial_aucpr_table )
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid && !xval ) {
            metric <- model.parts$tm@metrics$multinomial_aucpr_table
            if ( !is.null(metric) ) return(metric)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            v <- c(v,model.parts$tm@metrics$multinomial_aucpr_table)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                v <- c(v,model.parts$vm@metrics$multinomial_aucpr_table)
                v_names <- c(v_names,"valid")
            }
        }
        if ( xval ) {
            if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
            else {
                v <- c(v,model.parts$xm@metrics$multinomial_aucpr_table)
                v_names <- c(v_names,"xval")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No PR AUC table for ", class(object)))
    invisible(NULL)
}

#' @rdname h2o.aucpr
#' @export
h2o.pr_auc <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  .Deprecated("h2o.aucpr")
  h2o.aucpr(object, train, valid, xval)
}

#' Retrieve the mean per class error
#'
#' Retrieves the mean per class error from an \linkS4class{H2OBinomialMetrics}.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training mean per class error value is returned. If more
#' than one parameter is set to TRUE, then a named vector of mean per class errors are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OBinomialMetrics} object.
#' @param train Retrieve the training mean per class error
#' @param valid Retrieve the validation mean per class error
#' @param xval Retrieve the cross-validation mean per class error
#' @seealso \code{\link{h2o.mse}} for MSE, and \code{\link{h2o.metric}} for the
#'          various threshold metrics. See \code{\link{h2o.performance}} for
#'          creating H2OModelMetrics objects.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#'
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' perf <- h2o.performance(model, prostate)
#' h2o.mean_per_class_error(perf)
#' h2o.mean_per_class_error(model, train=TRUE)
#' }
#' @export
h2o.mean_per_class_error <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$mean_per_class_error )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$mean_per_class_error
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$mean_per_class_error)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$mean_per_class_error)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$mean_per_class_error)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No mean per class error for ", class(object)))
  invisible(NULL)
}

#'
#' Retrieve the Akaike information criterion (AIC) value
#'
#' Retrieves the AIC value.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training AIC value is returned. If more
#' than one parameter is set to TRUE, then a named vector of AICs are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} or \linkS4class{H2OModelMetrics}.
#' @param train Retrieve the training AIC
#' @param valid Retrieve the validation AIC
#' @param xval Retrieve the cross-validation AIC
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' p_sid <- h2o.runif(prostate)
#' prostate_train <- prostate[p_sid > .2,]
#' prostate_glm <- h2o.glm(x = 3:7, y = 2, training_frame = prostate_train)
#' aic_basic <- h2o.aic(prostate_glm)
#' print(aic_basic)
#' }
#' @export
h2o.aic <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$AIC )
  if( is(object, "H2OModel") ) {
      if (('calc_like' %in% names(object@allparameters)) && !object@allparameters$calc_like) {
        warning_message <- paste0("This is the AIC function using the simplified negative log likelihood used during ",
                                  "training for speedup. To see the correct value, set calc_like=True, ",
                                  "retrain and call h2o.aic(model).")
        warning(warning_message)
      }
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$AIC
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$AIC)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$AIC)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$AIC)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No AIC for ", class(object)))
  invisible(NULL)
}


#'
#' Retrieve the log likelihood value
#'
#' Retrieves the log likelihood value.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training log likelihood value is returned. If more
#' than one parameter is set to TRUE, then a named vector of log likelihoods is returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} or \linkS4class{H2OModelMetrics}.
#' @param train Retrieve the training log likelihood
#' @param valid Retrieve the validation log likelihood
#' @param xval Retrieve the cross-validation log likelihood
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' p_sid <- h2o.runif(prostate)
#' prostate_train <- prostate[p_sid > .2,]
#' prostate_glm <- h2o.glm(x = 3:7, y = 2, training_frame = prostate_train)
#' ll_basic <- h2o.loglikelihood(prostate_glm)
#' print(ll_basic)
#' }
#' @export
h2o.loglikelihood <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
    if( is(object, "H2OModelMetrics") ) return( object@metrics$loglikelihood )
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        if ( !train && !valid && !xval ) {
            metric <- model.parts$tm@metrics$loglikelihood
            if ( !is.null(metric) ) return(metric)
        }
        v <- c()
        v_names <- c()
        if ( train ) {
            v <- c(v,model.parts$tm@metrics$loglikelihood)
            v_names <- c(v_names,"train")
        }
        if ( valid ) {
            if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
            else {
                v <- c(v,model.parts$vm@metrics$loglikelihood)
                v_names <- c(v_names,"valid")
            }
        }
        if ( xval ) {
            if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
            else {
                v <- c(v,model.parts$xm@metrics$loglikelihood)
                v_names <- c(v_names,"xval")
            }
        }
        if ( !is.null(v) ) {
            names(v) <- v_names
            if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
        }
    }
    warning(paste0("No loglikelihood for ", class(object)))
    invisible(NULL)
}

#'
#' Retrieve the R2 value
#'
#' Retrieves the R2 value from an H2O model.
#' Will return R^2 for GLM Models.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training R2 value is returned. If more
#' than one parameter is set to TRUE, then a named vector of R2s are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param train Retrieve the training R2
#' @param valid  Retrieve the validation set R2 if a validation set was passed in during model build time.
#' @param xval Retrieve the cross-validation R2
#' @examples
#' \dontrun{
#' library(h2o)
#'
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#'
#' m <- h2o.glm(x = 2:5, y = 1, training_frame = fr)
#'
#' h2o.r2(m)
#' }
#' @export
h2o.r2 <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$r2 )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$r2
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$r2)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$r2)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$r2)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No R2 for ", class(object)))
  invisible(NULL)
}

#'
#' Retrieve the Mean Residual Deviance value
#'
#' Retrieves the Mean Residual Deviance value from an H2O model.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training Mean Residual Deviance value is returned. If more
#' than one parameter is set to TRUE, then a named vector of Mean Residual Deviances are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param train Retrieve the training Mean Residual Deviance
#' @param valid Retrieve the validation Mean Residual Deviance
#' @param xval Retrieve the cross-validation Mean Residual Deviance
#' @examples
#' \dontrun{
#' library(h2o)
#'
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#'
#' m <- h2o.deeplearning(x = 2:5, y = 1, training_frame = fr)
#'
#' h2o.mean_residual_deviance(m)
#' }
#' @export
h2o.mean_residual_deviance <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$mean_residual_deviance )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$mean_residual_deviance
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$mean_residual_deviance)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$mean_residual_deviance)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$mean_residual_deviance)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No mean residual deviance for ", class(object)))
  invisible(NULL)
}

#' Retrieve HGLM ModelMetrics
#'
#' @param object an H2OModel object or H2OModelMetrics.
#' @export
h2o.HGLMMetrics <- function(object) {
    if( is(object, "H2OModel") ) {
        model.parts <- .model.parts(object)
        return(model.parts$tm@metrics)
    }
    warning(paste0("No HGLM Metric for ",class(object)))
    invisible(NULL)
}

#' Retrieve the GINI Coefficcient
#'
#' Retrieves the GINI coefficient from an \linkS4class{H2OBinomialMetrics}.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training GINIvalue is returned. If more
#' than one parameter is set to TRUE, then a named vector of GINIs are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object an \linkS4class{H2OBinomialMetrics} object.
#' @param train Retrieve the training GINI Coefficcient
#' @param valid Retrieve the validation GINI Coefficcient
#' @param xval Retrieve the cross-validation GINI Coefficcient
#' @seealso \code{\link{h2o.auc}} for AUC,  \code{\link{h2o.giniCoef}} for the
#'          GINI coefficient, and \code{\link{h2o.metric}} for the various
#'          threshold metrics. See \code{\link{h2o.performance}} for creating 
#'          H2OModelMetrics objects.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#'
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' perf <- h2o.performance(model, prostate)
#' h2o.giniCoef(perf)
#' }
#' @export
h2o.giniCoef <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if(is(object, "H2OModelMetrics")) return( object@metrics$Gini )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$Gini
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$Gini)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$Gini)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$Gini)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No Gini for ",class(object)))
  invisible(NULL)
}

#'
#' Return the coefficients table with coefficients, standardized coefficients, p-values, z-values and std-error for GLM models
#'
#' @param object An \linkS4class{H2OModel} object.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "cylinders"
#' cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_glm <- h2o.glm(seed = 1234, 
#'                     lambda=0.0,
#'                     compute_p_values=TRUE,
#'                     x = predictors, 
#'                     y = response, 
#'                     training_frame = train, 
#'                     validation_frame = valid)
#' h2o.coef_with_p_values(cars_glm)
#' }
#' @export
h2o.coef_with_p_values <- function(object) {
  if (is(object, "H2OModel") && object@algorithm %in% c("glm")) {
    if (object@parameters$compute_p_values) {
      object@model$coefficients_table
    } else {
      stop("p-values, z-values and std_error are not found in model.  Make sure to set compute_p_values=TRUE.")
    }
  } else {
    stop("p-values, z-values and std_error are only found in GLM.")
  }
}

#'
#' Return the variable inflation factors associated with numerical predictors for GLM models.
#'
#' @param object An \linkS4class{H2OModel} object.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "cylinders"
#' cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_glm <- h2o.glm(seed = 1234, 
#'                     lambda=0.0,
#'                     compute_p_values=TRUE,
#'                     generate_variable_inflation_factors=TRUE,     
#'                     x = predictors, 
#'                     y = response, 
#'                     training_frame = train, 
#'                     validation_frame = valid)
#' h2o.get_variable_inflation_factors(cars_glm)
#' }
#' @export
h2o.get_variable_inflation_factors <- function(object) {
  if (is(object, "H2OModel") && object@algorithm %in% c("glm")) {
    if (object@parameters$generate_variable_inflation_factors) {
      structure(object@model$variable_inflation_factors, names = object@model$vif_predictor_names)
    } else {
      stop("variable inflation factors are not found in model.  Make sure to set enable_variable_inflation_factors=TRUE.")
    }
  } else {
    stop("variable inflation factors are only found in GLM models with numerical predictors.")
  }
}

#'
#' Return the coefficients that can be applied to the non-standardized data.
#'
#' Note: standardize = True by default. If set to False, then coef() returns the coefficients that are fit directly.
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param predictorSize predictor subset size.  If specified, will only return model coefficients of that subset size.  If
#'          not specified will return a lists of model coefficient dicts for all predictor subset size.
#' @param object an \linkS4class{H2OModel} object.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "cylinders"
#' cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_glm <- h2o.glm(balance_classes = TRUE, 
#'                     seed = 1234, 
#'                     x = predictors, 
#'                     y = response, 
#'                     training_frame = train, 
#'                     validation_frame = valid)
#' h2o.coef(cars_glm)
#' }
#' @export
h2o.coef <- function(object, predictorSize = -1) {
  if (is(object, "H2OModel") &&
      object@algorithm %in% c("glm", "gam", "coxph", "modelselection")) {
    if ((object@algorithm == "glm" ||
         object@algorithm == "gam") &&
        (object@allparameters$family %in% c("multinomial", "ordinal"))) {
      grabCoeff(object@model$coefficients_table, "coefs_class", FALSE)
    } else {
      if (object@algorithm == "modelselection") {
        if (object@allparameters$mode == "maxrsweep" &&
            !object@allparameters$build_glm_model) {
          numModels <- length(object@model$best_r2_values)
          maxPredNumbers <- object@parameters$max_predictor_number
          stopifnot(
            "predictorSize (predictor subset size) must be between 0 and the total number of predictors used." = predictorSize != 0,
            "predictorSize (predictor subset size) cannot exceed the total number of predictors used." = predictorSize <= maxPredNumbers
          )
          if (predictorSize < 0) {
            coeffs <- vector("list", numModels)
            for (index in seq(numModels)) {
              coeffs[[index]] <- structure(object@model$coefficient_values[[index]], names=object@model$coefficient_names[[index]])
            }
            return(coeffs)
          } else {
            return(structure(object@model$coefficient_values[[predictorSize]], names=object@model$coefficient_names[[predictorSize]]))    
          }
        } else {
          modelIDs <- object@model$best_model_ids
          numModels <- length(modelIDs)
          mode <- object@parameters$mode
          maxPredNumbers <- numModels
          if (mode == "backward")
            maxPredNumbers <- length(object@model$best_predictors_subset[[numModels]])
          stopifnot(
            "predictorSize (predictor subset size) must be between 0 and the total number of predictors used." = predictorSize != 0,
            "predictorSize (predictor subset size) cannot exceed the total number of predictors used." = predictorSize <= maxPredNumbers
            )
          if (predictorSize > 0) {
            # subset size was specified
            if (mode == "backward") {
              return(grabOneModelCoef(
                modelIDs,
                numModels - (maxPredNumbers - predictorSize),
                FALSE
              ))
            } else {
              return(grabOneModelCoef(modelIDs, predictorSize, FALSE))
            }
          } else {
            coeffs <- vector("list", numModels)
            for (index in seq(numModels)) {
              coeffs[[index]] <- grabOneModelCoef(modelIDs, index, FALSE)
            }
            return(coeffs)
          }
        }
      } else {
        structure(
          object@model$coefficients_table$coefficients,
          names = object@model$coefficients_table$names
        )
      }
    }
  } else {
    stop("Can only extract coefficients from GAM, GLM and CoxPH models")
  }
}

grabOneModelCoef <- function(modelIDs, index, standardized) {
  oneModel <- h2o.getModel(modelIDs[[index]]$name)
  if (standardized) {
    return(h2o.coef_norm(oneModel))
  } else {
      return(h2o.coef(oneModel))
  }
}

#'
#' Extracts a list of H2OFrames containing regression influence diagnostics for predictor subsets of various sizes or
#' just one H2OFrame containing regression influence diagnostics for predictor subsets of one fixed size
#'
#' @param model an \linkS4class{H2OModel} object.
#' @param predictorSize predictor subset size.  If specified, will only return model coefficients of that subset size.  If
#'          not specified will return a lists of model coefficient dicts for all predictor subset size.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "acceleration"
#' cars_model <- h2o.modelSelection(y=response, 
#'                                  x=predictors, 
#'                                  training_frame = cars, 
#'                                  min_predictor_number=2, 
#'                                  mode="backward", 
#'                                  influence="dfbetas",
#'                                  lambda=0.0,
#'                                  family="gaussian")
#' rid_frame <- h2o.get_regression_influence_diagnostics(cars_model, predictorSize=3)
#' }
#' @export   
h2o.get_regression_influence_diagnostics <- function(model, predictorSize = -1) {
    mode <- model@allparameters$mode
    if( is(model, "H2OModel") && (model@algorithm=='modelselection')) {
        if ((mode == 'maxrsweep') && (model@allparameters$build_glm_model==FALSE)) {
            stop("regression influence diagnostics are only available when GLM models are built.  For mode='maxrsweep', make
      sure build_glm_model to TRUE.")
        } else {
            if (model@allparameters$influence == "dfbetas") {
                modelIDs <- model@model$best_model_ids
                numModels <- length(modelIDs)
                if (predictorSize < 0) {    # return a list of frames
                    ridFrames <- vector("list", numModels)
                    for (index in seq(numModels)) {
                        oneModel <- h2o.getModel(modelIDs[[index]]$name)
                        ridFrames[[index]] <- h2o.getFrame(oneModel@model$regression_influence_diagnostics$name)
                    }
                    return(ridFrames)
                } else {  # only return one frame
                    maxPredNumbers <- numModels
                    index <- predictorSize
                    if (mode == "backwards") {
                        maxPredNumbers <- length(model@model$best_predictors_subset[[numModels]])
                        index <- numModels-(maxPredNumbers-predictorSize)
                    }
                    oneModel <- h2o.getModel(modelIDs[[index]]$name)
                    return(h2o.getFrame(oneModel@model$regression_influence_diagnostics$name))
                }
           } else {
                stop("regression influence diagnostic is only available when infuence='dfbetas' for GLM binomial and
                 gaussian families.")
            }
        }
    } else if (is(model, "H2OModel") && (model@algorithm=="glm")) {
        if (model@allparameters$influence == 'dfbetas') {
            return(h2o.getFrame(model@model$regression_influence_diagnostics$name))
        } else {
            stop("influence needs to be set to 'dfbetas'.")
        }
    }
}

#'
#' Extracts the final training negative log likelihood of a GLM model.
#'
#' @param model an \linkS4class{H2OModel} object.
#' @return The final training negative log likelihood of a GLM model.
#' 
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "acceleration"
#' cars_model <- h2o.glm(y=response, 
#'                        x=predictors, 
#'                        training_frame = cars, 
#'                        family="gaussian",
#'                        generate_scoring_history=TRUE)
#' nllValue <- h2o.negative_log_likelihood(cars_model)
#' }
#' @export 
h2o.negative_log_likelihood <- function(model) {
    if (model@allparameters$calc_like) {
        warning_message <- paste0("This is the simplified negative log likelihood function used during training for speedup. ", 
                                 "To see the correct value call h2o.loglikelihood(model).")
    } else {
        warning_message <- paste0("This is the simplified negative log likelihood function used during training for speedup. ", 
                                 "To see the correct value, set calc_like=True, retrain and call h2o.loglikelihood(model).")
    }
    warning(warning_message)
    return(extract_scoring_history(model, "negative_log_likelihood"))
}

#'
#' Extracts the final training average objective function of a GLM model.
#'
#' @param model an \linkS4class{H2OModel} object.
#' @return The final training average objective of a GLM model.
#' 
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "acceleration"
#' cars_model <- h2o.glm(y=response, 
#'                        x=predictors, 
#'                        training_frame = cars, 
#'                        family="gaussian",
#'                        generate_scoring_history=TRUE)
#' objValue <- h2o.average_objective(cars_model)
#' }
#' @export 
h2o.average_objective <- function(model) {

    warning_message <- paste0("This objective function is calculated based on the simplified negative log likelihood ",
                             "function used during training for speedup.")
    warning(warning_message)
    return(extract_scoring_history(model, "objective"))
}

extract_scoring_history <- function(model, value) {
  if (is(model, "H2OModel") && (model@algorithm=='glm')) {
      if (model@allparameters$generate_scoring_history==TRUE) {
          scHist <- model@model$scoring_history
          return(scHist[nrow(scHist), value])
      } else {
          stop("negative_log_likelihood and average_objection functions can only be extracted when generate_scoring_history=TRUE for now.")
      }
  } else {
      stop("negative_log_likelihood and average_objection functions are only available for GLM models.")
  }
}

#'
#' Return coefficients fitted on the standardized data (requires standardize = True, which is on by default). These coefficients can be used to evaluate variable importance.
#'
#' @param object an \linkS4class{H2OModel} object.
#' @param predictorSize predictor subset size.  If specified, will only return model coefficients of that subset size.  If
#'          not specified will return a lists of model coefficient dicts for all predictor subset size.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "cylinders"
#' cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_glm <- h2o.glm(balance_classes = TRUE, 
#'                     seed = 1234, 
#'                     x = predictors, 
#'                     y = response, 
#'                     training_frame = train, 
#'                     validation_frame = valid)
#' h2o.coef_norm(cars_glm)
#' }
#' @export
h2o.coef_norm <- function(object, predictorSize=-1) {
  if (is(object, "H2OModel") &&
      (object@algorithm %in% c("glm", "gam", "coxph", "modelselection"))) {
    if (object@algorithm == "modelselection") {
      if (object@allparameters$mode == "maxrsweep" &&
          !object@allparameters$build_glm_model) {
        numModels <- length(object@model$best_r2_values)
        maxPredNumbers <- object@parameters$max_predictor_number
        stopifnot(
            "predictorSize (predictor subset size) must be between 0 and the total number of predictors used." = predictorSize != 0,
            "predictorSize (predictor subset size) cannot exceed the total number of predictors used." = predictorSize <= maxPredNumbers
        )
        if (predictorSize < 0) {
          coeffs <- vector("list", numModels)
          for (index in seq(numModels)) {
            coeffs[[index]] <-
              structure(
                object@model$coefficient_values_normalized[[index]],
                names = object@model$coefficient_names[[index]]
              )
          }
          return(coeffs)
        } else {
          return(structure(object@model$coefficient_values_normalized[[predictorSize]],
                    names = object@model$coefficient_names[[predictorSize]]))
        }
        
        if (predictorSize < 0) {
          structure(names=object@model$coefficient_names, object@model$coefficient_values_normalized)
        } else {
          structure(names=object@model$coefficient_names[[predictorSize]], object@model$coefficient_values_normalized[[predictorSize]])    
        }        
      } else {
        modelIDs <- object@model$best_model_ids
        numModels = length(modelIDs)
        mode <- object@parameters$mode
        maxPredNumbers <- numModels
        stopifnot(
            "predictorSize (predictor subset size) must be between 0 and the total number of predictors used." = predictorSize != 0,
            "predictorSize (predictor subset size) cannot exceed the total number of predictors used." = predictorSize <= maxPredNumbers
        )
        if (predictorSize > 0) {
          # subset size was specified
          if (mode == "backward") {
            return(grabOneModelCoef(
              modelIDs,
              numModels - (maxPredNumbers - predictorSize),
              TRUE
            ))
          } else {
            return(grabOneModelCoef(modelIDs, predictorSize, TRUE))
          }
        } else {
          coeffs <- vector("list", numModels)
          for (index in seq(numModels)) {
            coeffs[[index]] <- grabOneModelCoef(modelIDs, index, TRUE)
          }
          return(coeffs)
        }
      }
    }
    if (object@allparameters$family %in% c("multinomial", "ordinal")) {
      grabCoeff(object@model$coefficients_table,
                "std_coefs_class",
                TRUE)
    } else {
      structure(
        object@model$coefficients_table$standardized_coefficients,
        names = object@model$coefficients_table$names
      )
    }
  } else {
    stop("Can only extract coefficients from GAMs/GLMs")
  }
}

grabCoeff <- function(tempTable, nameStart, standardize=FALSE) {
    coeffNamesPerClass <- tempTable$names # contains coeff names per class
    totTableLength <- length(tempTable)
    startIndex <- 2
    endIndex <- (totTableLength-1)/2+1
    if (standardize) {
        startIndex <- (totTableLength-1)/2+2   # starting index for standardized coefficients
        endIndex <- totTableLength
    }
    coeffClassNames <- c("coefficient_names")
    coeffPerClassAll <- list(coefficients_names=coeffNamesPerClass)
    cindex <- 0
    for (index in c(startIndex:endIndex)) {
        vals <- tempTable[,index]
        coeffClassNames <- c(coeffClassNames, paste(nameStart, cindex, sep="_"))
        cindex <- cindex+1
        coeffPerClassAll[[cindex+1]] <- vals
    }
    structure(coeffPerClassAll, names=coeffClassNames)
}

#' Retrieves Mean Squared Error Value
#'
#' Retrieves the mean squared error value from an \linkS4class{H2OModelMetrics}
#' object.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training MSEvalue is returned. If more
#' than one parameter is set to TRUE, then a named vector of MSEs are returned, where the names are "train", "valid"
#' or "xval".
#'
#' This function only supports \linkS4class{H2OBinomialMetrics},
#' \linkS4class{H2OMultinomialMetrics}, and \linkS4class{H2ORegressionMetrics} objects.
#'
#' @param object An \linkS4class{H2OModelMetrics} object of the correct type.
#' @param train Retrieve the training MSE
#' @param valid Retrieve the validation MSE
#' @param xval Retrieve the cross-validation MSE
#' @seealso \code{\link{h2o.auc}} for AUC, \code{\link{h2o.mse}} for MSE, and
#'          \code{\link{h2o.metric}} for the various threshold metrics. See
#'          \code{\link{h2o.performance}} for creating H2OModelMetrics objects.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#'
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' perf <- h2o.performance(model, prostate)
#' h2o.mse(perf)
#' }
#' @export
h2o.mse <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$MSE )
  if( is(object, "H2OModel") ) {
    metrics <- NULL # break out special for clustering vs the rest
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$MSE
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      if( is(object, "H2OClusteringModel") ) v <- model.parts$tm@metrics$centroid_stats$within_cluster_sum_of_squares
      else v <- c(v,model.parts$tm@metrics$MSE)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        if( is(object, "H2OClusteringModel") ) v <- model.parts$vm@metrics$centroid_stats$within_cluster_sum_of_squares
        else v <- c(v,model.parts$vm@metrics$MSE)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        if( is(object, "H2OClusteringModel") ) v <- model.parts$xm@metrics$centroid_stats$within_cluster_sum_of_squares
        else v <- c(v,model.parts$xm@metrics$MSE)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No MSE for ",class(object)))
  invisible(NULL)
}

#' Retrieves Root Mean Squared Error Value
#'
#' Retrieves the root mean squared error value from an \linkS4class{H2OModelMetrics}
#' object.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training RMSEvalue is returned. If more
#' than one parameter is set to TRUE, then a named vector of RMSEs are returned, where the names are "train", "valid"
#' or "xval".
#'
#' This function only supports \linkS4class{H2OBinomialMetrics},
#' \linkS4class{H2OMultinomialMetrics}, and \linkS4class{H2ORegressionMetrics} objects.
#'
#' @param object An \linkS4class{H2OModelMetrics} object of the correct type.
#' @param train Retrieve the training RMSE
#' @param valid Retrieve the validation RMSE
#' @param xval Retrieve the cross-validation RMSE
#' @seealso \code{\link{h2o.auc}} for AUC, \code{\link{h2o.mse}} for RMSE, and
#'          \code{\link{h2o.metric}} for the various threshold metrics. See
#'          \code{\link{h2o.performance}} for creating H2OModelMetrics objects.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#'
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' perf <- h2o.performance(model, prostate)
#' h2o.rmse(perf)
#' }
#' @export
h2o.rmse <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$RMSE )
  if( is(object, "H2OModel") ) {
    metrics <- NULL # break out special for clustering vs the rest
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$RMSE
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      if( is(object, "H2OClusteringModel") ) v <- model.parts$tm@metrics$centroid_stats$within_cluster_sum_of_squares
      else v <- c(v,model.parts$tm@metrics$RMSE)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        if( is(object, "H2OClusteringModel") ) v <- model.parts$vm@metrics$centroid_stats$within_cluster_sum_of_squares
        else v <- c(v,model.parts$vm@metrics$RMSE)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        if( is(object, "H2OClusteringModel") ) v <- model.parts$xm@metrics$centroid_stats$within_cluster_sum_of_squares
        else v <- c(v,model.parts$xm@metrics$RMSE)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No RMSE for ",class(object)))
  invisible(NULL)
}

#'
#' Retrieve the Mean Absolute Error Value
#'
#' Retrieves the mean absolute error (MAE) value from an H2O model.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training MAE value is returned. If more
#' than one parameter is set to TRUE, then a named vector of MAEs are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param train Retrieve the training MAE
#' @param valid  Retrieve the validation set MAE if a validation set was passed in during model build time.
#' @param xval Retrieve the cross-validation MAE
#' @examples
#' \dontrun{
#' library(h2o)
#'
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#'
#' m <- h2o.deeplearning(x = 2:5, y = 1, training_frame = fr)
#'
#' h2o.mae(m)
#' }
#' @export
h2o.mae <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$mae )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$mae
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$mae)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$mae)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$mae)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No MAE for ", class(object)))
  invisible(NULL)
}

#'
#' Retrieve the Root Mean Squared Log Error
#'
#' Retrieves the root mean squared log error (RMSLE) value from an H2O model.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training rmsle value is returned. If more
#' than one parameter is set to TRUE, then a named vector of rmsles are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param train Retrieve the training rmsle
#' @param valid  Retrieve the validation set rmsle if a validation set was passed in during model build time.
#' @param xval Retrieve the cross-validation rmsle
#' @examples
#' \dontrun{
#' library(h2o)
#'
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#'
#' m <- h2o.deeplearning(x = 2:5, y = 1, training_frame = fr)
#'
#' h2o.rmsle(m)
#' }
#' @export
h2o.rmsle <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$rmsle )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$rmsle
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$rmsle)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$rmsle)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$rmsle)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No rmsle for ", class(object)))
  invisible(NULL)
}

#' Retrieve the Log Loss Value
#'
#' Retrieves the log loss output for a \linkS4class{H2OBinomialMetrics} or
#' \linkS4class{H2OMultinomialMetrics} object
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training Log Loss value is returned. If more
#' than one parameter is set to TRUE, then a named vector of Log Losses are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object a \linkS4class{H2OModelMetrics} object of the correct type.
#' @param train Retrieve the training Log Loss
#' @param valid Retrieve the validation Log Loss
#' @param xval Retrieve the cross-validation Log Loss
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "economy_20mpg"
#' cars_splits <- h2o.splitFrame(data =  cars, ratios = .8, seed = 1234)
#' train <- cars_splits[[1]]
#' valid <- cars_splits[[2]]
#' car_drf <- h2o.randomForest(x = predictors, 
#'                             y = response, 
#'                             training_frame = train, 
#'                             validation_frame = valid)
#' h2o.logloss(car_drf, train = TRUE, valid = TRUE)
#' }
#' @export
h2o.logloss <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$logloss )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$logloss
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$logloss)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$logloss)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$logloss)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste("No log loss for",class(object)))
  invisible(NULL)
}


#'
#' Retrieve per-variable split information for a given Isolation Forest model.
#' Output will include:
#' - count - The number of times a variable was used to make a split.
#' - aggregated_split_ratios - The split ratio is defined as "abs(#left_observations - #right_observations) / #before_split".
#'                             Even splits (#left_observations approx the same as #right_observations) contribute
#'                             less to the total aggregated split ratio value for the given feature;
#'                             highly imbalanced splits (eg. #left_observations >> #right_observations) contribute more.
#' - aggregated_split_depths - The sum of all depths of a variable used to make a split. (If a variable is used
#'                             on level N of a tree, then it contributes with N to the total aggregate.)
#' @param object An Isolation Forest model represented by \linkS4class{H2OModel} object.
#' @export
h2o.varsplits <- function(object) {
  if( is(object, "H2OModel") ) {
    vi <- object@model$variable_splits
    if( is.null(vi) ) {
      warning("This model doesn't have variable splits information, only Isolation Forest can be used with h2o.varsplits().", call. = FALSE)
      return(invisible(NULL))
    }
    vi
  } else {
    warning( paste0("No variable importances for ", class(object)) )
    return(NULL)
  }
}

#'
#' Retrieve Model Score History
#'
#' @param object An \linkS4class{H2OModel} object.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "economy_20mpg"
#' cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_gbm <- h2o.gbm(x = predictors, y = response, 
#'                     training_frame = train, 
#'                     validation_frame = valid, 
#'                     seed = 1234)
#' h2o.scoreHistory(cars_gbm)
#' }
#' @export
h2o.scoreHistory <- function(object) {
  o <- object
  if( is(o, "H2OModel") ) {
    sh <- o@model$scoring_history
    if( is.null(sh) ) return(NULL)
    sh
  } else {
    warning( paste0("No score history for ", class(o)) )
    return(NULL)
  }
}

#'
#' Retrieve GLM Model Score History buried in GAM model
#' @param object An \linkS4class{H2OModel} object.
#' @export
h2o.scoreHistoryGAM <- function(object) {
    return(object@model$glm_scoring_history)
}

#'
#' Retrieve actual number of trees for tree algorithms
#'
#' @param object An \linkS4class{H2OModel} object.
#' @export
h2o.get_ntrees_actual <- function(object) {
    o <- object
    if( is(o, "H2OModel") ) {
        if(o@algorithm == "gbm" | o@algorithm == "drf"| o@algorithm == "isolationforest"| o@algorithm == "xgboost" | o@algorithm == "extendedisolationforest" | o@algorithm == "upliftdrf"){
            sh <- o@model$model_summary['number_of_trees'][,1]
            if( is.null(sh) ) return(NULL)
            sh
        } else {
            warning( paste0("No actual number of trees for this model") )
            return(NULL)
        }
    } else {
        warning( paste0("No actual number of trees for ", class(o)) )
        return(NULL)
    }
}

#' Feature interactions and importance, leaf statistics and split value histograms in a tabular form.
#' Available for XGBoost and GBM.
#'
#' Metrics:
#' Gain - Total gain of each feature or feature interaction.
#' FScore - Amount of possible splits taken on a feature or feature interaction.
#' wFScore - Amount of possible splits taken on a feature or feature interaction weighed by 
#' the probability of the splits to take place.
#' Average wFScore - wFScore divided by FScore.
#' Average Gain - Gain divided by FScore.
#' Expected Gain - Total gain of each feature or feature interaction weighed by the probability to gather the gain.
#' Average Tree Index
#' Average Tree Depth
#'
#' @param model A trained xgboost model.
#' @param max_interaction_depth Upper bound for extracted feature interactions depth. Defaults to 100.
#' @param max_tree_depth Upper bound for tree depth. Defaults to 100.
#' @param max_deepening Upper bound for interaction start deepening (zero deepening => interactions 
#' starting at root only). Defaults to -1.
#'
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' boston <- h2o.importFile(
#'        "https://s3.amazonaws.com/h2o-public-test-data/smalldata/gbm_test/BostonHousing.csv",
#'         destination_frame="boston"
#'         )
#' boston_xgb <- h2o.xgboost(training_frame = boston, y = "medv", seed = 1234)
#' feature_interactions <- h2o.feature_interaction(boston_xgb)
#' }
#' @export
h2o.feature_interaction <- function(model, max_interaction_depth = 100, max_tree_depth = 100, max_deepening = -1) {
    o <- model
    if (is(o, "H2OModel")) {
        if (o@algorithm == "gbm" | o@algorithm == "xgboost"){
            parms <- list()
            parms$model_id <- model@model_id
            parms$max_interaction_depth <- max_interaction_depth
            parms$max_tree_depth <- max_tree_depth
            parms$max_deepening <- max_deepening
            
            json <- .h2o.doSafePOST(urlSuffix = "FeatureInteraction", parms=parms)
            source <- .h2o.fromJSON(jsonlite::fromJSON(json,simplifyDataFrame=FALSE))
            if(is.null(source$feature_interaction)){
                warning(paste0("There is no feature interaction for this model."))
                return(NULL)
            }
            return(source$feature_interaction)
        } else {
            warning(paste0("No calculation available for this model"))
            return(NULL)
        }
    } else {
        warning(paste0("No calculation available for ", class(o)))
        return(NULL)
    }
}


#' Calculates Friedman and Popescu's H statistics, in order to test for the presence of an interaction between specified variables in h2o gbm and xgb models.
#' H varies from 0 to 1. It will have a value of 0 if the model exhibits no interaction between specified variables and a correspondingly larger value for a 
#' stronger interaction effect between them. NaN is returned if a computation is spoiled by weak main effects and rounding errors.
#'
#' This statistic can be calculated only for numerical variables. Missing values are supported.
#' 
#' See Jerome H. Friedman and Bogdan E. Popescu, 2008, "Predictive learning via rule ensembles", *Ann. Appl. Stat.*
#' **2**:916-954, http://projecteuclid.org/download/pdfview_1/euclid.aoas/1223908046, s. 8.1.
#'
#' @param model A trained gradient-boosting model.  
#' @param frame A frame that current model has been fitted to.
#' @param variables Variables of the interest.
#'
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate.hex <- h2o.importFile(
#'        "https://s3.amazonaws.com/h2o-public-test-data/smalldata/logreg/prostate.csv",
#'         destination_frame="prostate.hex"
#'         )
#' prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
#' prostate.hex$RACE <- as.factor(prostate.hex$RACE)
#' prostate.h2o <- h2o.gbm(x = 3:9, y = "CAPSULE", training_frame = prostate.hex, 
#' distribution = "bernoulli", ntrees = 100, max_depth = 5, min_rows = 10, learn_rate = 0.1)
#' h_val <- h2o.h(prostate.h2o, prostate.hex, c('DPROS','DCAPS'))
#' }
#' @export
h2o.h <- function(model, frame, variables) {
    o <- model
    if (is(o, "H2OModel")) {
        if (o@algorithm == "gbm" | o@algorithm == "xgboost"){
            parms <- list()
            parms$model_id <- model@model_id
            parms$frame <- h2o.getId(frame)
            parms$variables <- .collapse.char(variables)

            json <- .h2o.doSafePOST(urlSuffix = "FriedmansPopescusH", parms=parms)
            source <- .h2o.fromJSON(jsonlite::fromJSON(json,simplifyDataFrame=FALSE))

            return(source$h)
        } else {
            warning(paste0("No calculation available for this model"))
            return(NULL)
        }
    } else {
        warning(paste0("No calculation available for ", class(o)))
        return(NULL)
    }
}

#'
#' Retrieve the respective weight matrix
#'
#' @param object An \linkS4class{H2OModel} or \linkS4class{H2OModelMetrics}
#' @param matrix_id An integer, ranging from 1 to number of layers + 1, that specifies the weight matrix to return.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/chicago/chicagoCensus.csv"
#' census <- h2o.importFile(f)
#' census[, 1] <- as.factor(census[, 1])
#' dl_model <- h2o.deeplearning(x = c(1:3), y = 4, training_frame = census,
#'                             hidden = c(17, 191), 
#'                             epochs = 1,
#'                             balance_classes = FALSE,
#'                             export_weights_and_biases = TRUE)
#' h2o.weights(dl_model, matrix_id = 1)
#' }
#' @export
h2o.weights <- function(object, matrix_id=1){
  o <- object
  if( is(o, "H2OModel") ) {
    sh <- o@model$weights[[matrix_id]]
    if( is.null(sh) ) return(NULL)
    sh
  } else {
    warning( paste0("No weights for ", class(o)) )
    return(NULL)
  }
  h2o.getFrame(sh$name)
}

#'
#' Return the respective bias vector
#'
#' @param object An \linkS4class{H2OModel} or \linkS4class{H2OModelMetrics}
#' @param vector_id An integer, ranging from 1 to number of layers + 1, that specifies the bias vector to return.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/chicago/chicagoCensus.csv"
#' census <- h2o.importFile(f)
#' census[, 1] <- as.factor(census[, 1])
#' 
#' dl_model <- h2o.deeplearning(x = c(1:3), y = 4, training_frame = census,
#'                             hidden = c(17, 191),
#'                             epochs = 1, 
#'                             balance_classes = FALSE, 
#'                             export_weights_and_biases = TRUE)
#' h2o.biases(dl_model, vector_id = 1)
#' }
#' @export
h2o.biases <- function(object, vector_id=1){
  o <- object
  if( is(o, "H2OModel") ) {
    sh <- o@model$biases[[vector_id]]
    if( is.null(sh) ) return(NULL)
    sh
  } else {
    warning( paste0("No biases for ", class(o)) )
    return(NULL)
  }
  h2o.getFrame(sh$name)
}

#'
#' Retrieve the Hit Ratios
#'
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training Hit Ratios value is returned. If more
#' than one parameter is set to TRUE, then a named list of Hit Ratio tables are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param train Retrieve the training Hit Ratio
#' @param valid Retrieve the validation Hit Ratio
#' @param xval Retrieve the cross-validation Hit Ratio
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/iris/iris_wheader.csv"
#' iris <- h2o.importFile(f)
#' iris_split <- h2o.splitFrame(data = iris, ratios = 0.8, seed = 1234)
#' train <- iris_split[[1]]
#' valid <- iris_split[[2]]
#' 
#' iris_xgb <- h2o.xgboost(x = 1:4, y = 5, training_frame = train, validation_frame = valid)
#' hrt_iris <- h2o.hit_ratio_table(iris_xgb, valid = TRUE)
#' hrt_iris
#' }
#' @export
h2o.hit_ratio_table <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$hit_ratio_table )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$hit_ratio_table
      if ( !is.null(metric) ) return(metric)
    }
    v <- list()
    v_names <- c()
    if ( train ) {
      v[[length(v)+1]] <- model.parts$tm@metrics$hit_ratio_table
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v[[length(v)+1]] <- model.parts$vm@metrics$hit_ratio_table
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v[[length(v)+1]] <- model.parts$xm@metrics$hit_ratio_table
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  # if o is a data.frame, then the hrt was passed in -- just for pretty printing
  if( is(object, "data.frame") ) return(object)

  # warn if we got something unexpected...
  warning( paste0("No hit ratio table for ", class(object)) )
  invisible(NULL)
}

#' H2O Model Metric Accessor Functions
#'
#' A series of functions that retrieve model metric details.
#'
#' Many of these functions have an optional thresholds parameter. Currently
#' only increments of 0.1 are allowed. If not specified, the functions will
#' return all possible values. Otherwise, the function will return the value for
#' the indicated threshold.
#'
#' Currently, the these functions are only supported by
#' \linkS4class{H2OBinomialMetrics} objects.
#'
#' @param object An \linkS4class{H2OModelMetrics} object of the correct type.
#' @param thresholds (Optional) A value or a list of values between 0.0 and 1.0.
#'        If not set, then all thresholds will be returned.
#'        If "max", then the threshold maximizing the metric will be used.
#' @param metric (Optional) the metric to retrieve.
#'        If not set, then all metrics will be returned.
#' @param transform (Optional) a list describing a transformer for the given metric, if any.
#'        e.g. transform=list(op=foo_fn, name="foo") will rename the given metric to "foo"
#'             and apply function foo_fn to the metric values.
#' @return Returns either a single value, or a list of values.
#' @seealso \code{\link{h2o.auc}} for AUC, \code{\link{h2o.giniCoef}} for the
#'          GINI coefficient, and \code{\link{h2o.mse}} for MSE. See
#'          \code{\link{h2o.performance}} for creating H2OModelMetrics objects.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#'
#' prostate$CAPSULE <- as.factor(prostate$CAPSULE)
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' perf <- h2o.performance(model, prostate)
#' h2o.F1(perf)
#' }
#' @export
h2o.metric <- function(object, thresholds, metric, transform=NULL) {
  if (!is(object, "H2OModelMetrics")) stop(paste0("No ", metric, " for ",class(object)," .Should be a H2OModelMetrics object!"))
  if (is(object, "H2OBinomialMetrics")){
    avail_metrics <- names(object@metrics$thresholds_and_metric_scores)
    avail_metrics <- avail_metrics[!(avail_metrics %in% c('threshold', 'idx'))]
    if (missing(thresholds)) {
      if (missing(metric)) {
        metrics <- object@metrics$thresholds_and_metric_scores
      } else {
        h2o_metric <- sapply(metric, function(m) ifelse(m %in% avail_metrics, m, ifelse(m %in% names(.h2o.metrics_aliases), .h2o.metrics_aliases[m], m)))
        metrics <- object@metrics$thresholds_and_metric_scores[, c("threshold", h2o_metric)]
        if (!missing(transform)) {
          if ('op' %in% names(transform)) {
            metrics[h2o_metric] <- transform$op(metrics[h2o_metric])
          }
          if ('name' %in% names(transform)) {
            names(metrics) <- c("threshold", transform$name)
          }
        }
      }
    } else if (all(thresholds == 'max') && missing(metric)) {
      metrics <- object@metrics$max_criteria_and_metric_scores
    } else {
      if (missing(metric)) {
        h2o_metric <- avail_metrics
      } else {
        h2o_metric <- unlist(lapply(metric, function(m) ifelse(m %in% avail_metrics, m, ifelse(m %in% names(.h2o.metrics_aliases), .h2o.metrics_aliases[m], m))))
      }
      if (all(thresholds == 'max')) thresholds <- h2o.find_threshold_by_max_metric(object, h2o_metric)
      metrics <- lapply(thresholds, function(t,o,m) h2o.find_row_by_threshold(o, t)[, m], object, h2o_metric)
      if (!missing(transform) && 'op' %in% names(transform)) {
        metrics <- lapply(metrics, transform$op)
      }
    }
    return(metrics)
  }
  else {
    stop(paste0("No ", metric, " for ",class(object)))
  }
}

#' @rdname h2o.metric
#' @export
h2o.F0point5 <- function(object, thresholds){
  h2o.metric(object, thresholds, "f0point5")
}

#' @rdname h2o.metric
#' @export
h2o.F1 <- function(object, thresholds){
  h2o.metric(object, thresholds, "f1")
}

#' @rdname h2o.metric
#' @export
h2o.F2 <- function(object, thresholds){
  h2o.metric(object, thresholds, "f2")
}

#' @rdname h2o.metric
#' @export
h2o.accuracy <- function(object, thresholds){
  h2o.metric(object, thresholds, "accuracy")
}

#' @rdname h2o.metric
#' @export
h2o.error <- function(object, thresholds){
  h2o.metric(object, thresholds, "accuracy", transform=list(name="error", op=function(acc) 1 - acc))
}

#' @rdname h2o.metric
#' @export
h2o.maxPerClassError <- function(object, thresholds){
  h2o.metric(object, thresholds, "min_per_class_accuracy", transform=list(name="max_per_class_error", op=function(mpc_acc) 1 - mpc_acc))
}

#' @rdname h2o.metric
#' @export
h2o.mean_per_class_accuracy <- function(object, thresholds){
  h2o.metric(object, thresholds, "mean_per_class_accuracy")
}

#' @rdname h2o.metric
#' @export
h2o.mcc <- function(object, thresholds){
  h2o.metric(object, thresholds, "absolute_mcc")
}

#' @rdname h2o.metric
#' @export
h2o.precision <- function(object, thresholds){
  h2o.metric(object, thresholds, "precision")
}

#' @rdname h2o.metric
#' @export
h2o.tpr <- function(object, thresholds){
  h2o.metric(object, thresholds, "tpr")
}

#' @rdname h2o.metric
#' @export
h2o.fpr <- function(object, thresholds){
  h2o.metric(object, thresholds, "fpr")
}

#' @rdname h2o.metric
#' @export
h2o.fnr <- function(object, thresholds){
  h2o.metric(object, thresholds, "fnr")
}

#' @rdname h2o.metric
#' @export
h2o.tnr <- function(object, thresholds){
  h2o.metric(object, thresholds, "tnr")
}

#' @rdname h2o.metric
#' @export
h2o.recall <- function(object, thresholds){
  h2o.metric(object, thresholds, "tpr")
}

#' @rdname h2o.metric
#' @export
h2o.sensitivity <- function(object, thresholds){
  h2o.metric(object, thresholds, "tpr")
}

#' @rdname h2o.metric
#' @export
h2o.fallout <- function(object, thresholds){
  h2o.metric(object, thresholds, "fpr")
}

#' @rdname h2o.metric
#' @export
h2o.missrate <- function(object, thresholds){
  h2o.metric(object, thresholds, "fnr")
}

#' @rdname h2o.metric
#' @export
h2o.specificity <- function(object, thresholds){
  h2o.metric(object, thresholds, "tnr")
}

#' Find the threshold, give the max metric
#'
#' @rdname h2o.find_threshold_by_max_metric
#' @param object H2OBinomialMetrics
#' @param metric "F1," for example
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "economy_20mpg"
#' cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_gbm <- h2o.gbm(x = predictors, y = response, 
#'                     training_frame = train, validation_frame = valid, 
#'                     build_tree_one_node = TRUE , seed = 1234)
#' perf <- h2o.performance(cars_gbm, cars)
#' h2o.find_threshold_by_max_metric(perf, "fnr")
#' }
#' @export
h2o.find_threshold_by_max_metric <- function(object, metric) {
  if(!is(object, "H2OBinomialMetrics")) stop(paste0("No ", metric, " for ",class(object)))
  max_metrics <- object@metrics$max_criteria_and_metric_scores
  h2o_metric <- sapply(metric, function(m) ifelse(m %in% names(.h2o.metrics_aliases), .h2o.metrics_aliases[m], m))
  max_metrics[match(paste0("max ", h2o_metric), max_metrics$metric), "threshold"]
}

#' Find the threshold, give the max metric. No duplicate thresholds allowed
#'
#' @rdname h2o.find_row_by_threshold
#' @param object H2OBinomialMetrics
#' @param threshold number between 0 and 1
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "economy_20mpg"
#' cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_gbm <- h2o.gbm(x = predictors, y = response, 
#'                     training_frame = train, validation_frame = valid, 
#'                     build_tree_one_node = TRUE , seed = 1234)
#' perf <- h2o.performance(cars_gbm, cars)
#' h2o.find_row_by_threshold(perf, 0.5)
#' }
#' @export
h2o.find_row_by_threshold <- function(object, threshold) {
  if(!is(object, "H2OBinomialMetrics")) stop(paste0("No ", threshold, " for ",class(object)))
  tmp <- object@metrics$thresholds_and_metric_scores
  if( is.null(tmp) ) return(NULL)
  res <- tmp[abs(as.numeric(tmp$threshold) - threshold) < 1e-8,]  # relax the tolerance
  if( nrow(res) == 0L ) {
    # couldn't find any threshold within 1e-8 of the requested value, warn and return closest threshold
    row_num <- which.min(abs(tmp$threshold - threshold))
    closest_threshold <- tmp$threshold[row_num]
    warning( paste0("Could not find exact threshold: ", threshold, " for this set of metrics; using closest threshold found: ", closest_threshold, ". Run `h2o.predict` and apply your desired threshold on a probability column.") )
    return( tmp[row_num,] )
  }
  else if( nrow(res) > 1L ) res <- res[1L,]
  res
}

#'
#' Retrieve the Model Centers
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' fr <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_train.csv")
#' h2o.ceiling(fr[, 1])
#' }
#' @export
h2o.centers <- function(object) { as.data.frame(object@model$centers[,-1]) }

#'
#' Retrieve the Model Centers STD
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' fr <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_train.csv")
#' predictors <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")
#' km <- h2o.kmeans(x = predictors, training_frame = fr, k = 3, nfolds = 3)
#' h2o.centersSTD(km)
#' }
#' @export
h2o.centersSTD <- function(object) { as.data.frame(object@model$centers_std)[,-1] }

#'
#' Get the Within SS
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
#' @export
h2o.withinss <- function(object) { h2o.mse(object) }

#'
#' Get the total within cluster sum of squares.
#'
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training tot_withinss value is returned. If more
#' than one parameter is set to TRUE, then a named vector of tot_withinss' are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
#' @param train Retrieve the training total within cluster sum of squares
#' @param valid Retrieve the validation total within cluster sum of squares
#' @param xval Retrieve the cross-validation total within cluster sum of squares
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' fr <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_train.csv")
#' predictors <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")
#' km <- h2o.kmeans(x = predictors, training_frame = fr, k = 3, nfolds = 3)
#' h2o.tot_withinss(km, train = TRUE)
#' }
#' @export
h2o.tot_withinss <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  model.parts <- .model.parts(object)
  if ( !train && !valid && !xval ) return( model.parts$tm@metrics$tot_withinss )
  v <- c()
  v_names <- c()
  if ( train ) {
    v <- c(v,model.parts$tm@metrics$tot_withinss)
    v_names <- c(v_names,"train")
  }
  if ( valid ) {
    if( is.null(model.parts$vm) ) invisible(.warn.no.validation())
    else {
      v <- c(v,model.parts$vm@metrics$tot_withinss)
      v_names <- c(v_names,"valid")
    }
  }
  if ( xval ) {
    if( is.null(model.parts$xm) ) invisible(.warn.no.cross.validation())
    else {
      v <- c(v,model.parts$xm@metrics$tot_withinss)
      v_names <- c(v_names,"xval")
    }
  }
  names(v) <- v_names
  if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
}

#' Get the between cluster sum of squares
#'
#' Get the between cluster sum of squares.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training betweenss value is returned. If more
#' than one parameter is set to TRUE, then a named vector of betweenss' are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
#' @param train Retrieve the training between cluster sum of squares
#' @param valid Retrieve the validation between cluster sum of squares
#' @param xval Retrieve the cross-validation between cluster sum of squares
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' fr <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_train.csv")
#' predictors <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")
#' km <- h2o.kmeans(x = predictors, training_frame = fr, k = 3, nfolds = 3)
#' h2o.betweenss(km, train = TRUE)
#' }
#' @export
h2o.betweenss <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  model.parts <- .model.parts(object)
  if ( !train && !valid && !xval ) return( model.parts$tm@metrics$betweenss )
  v <- c()
  v_names <- c()
  if ( train ) {
    v <- c(v,model.parts$tm@metrics$betweenss)
    v_names <- c(v_names,"train")
  }
  if ( valid ) {
    if( is.null(model.parts$vm) ) invisible(.warn.no.validation())
    else {
      v <- c(v,model.parts$vm@metrics$betweenss)
      v_names <- c(v_names,"valid")
    }
  }
  if ( xval ) {
    if( is.null(model.parts$xm) ) invisible(.warn.no.cross.validation())
    else {
      v <- c(v,model.parts$xm@metrics$betweenss)
      v_names <- c(v_names,"xval")
    }
  }
  names(v) <- v_names
  if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
}

#'
#' Get the total sum of squares.
#'
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training totss value is returned. If more
#' than one parameter is set to TRUE, then a named vector of totss' are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
#' @param train Retrieve the training total sum of squares
#' @param valid Retrieve the validation total sum of squares
#' @param xval Retrieve the cross-validation total sum of squares
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' fr <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_train.csv")
#' predictors <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")
#' km <- h2o.kmeans(x = predictors, training_frame = fr, k = 3, nfolds = 3)
#' h2o.totss(km, train = TRUE)
#' }
#' @export
h2o.totss <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  model.parts <- .model.parts(object)
  if ( !train && !valid && !xval ) return( model.parts$tm@metrics$totss )
  v <- c()
  v_names <- c()
  if ( train ) {
    v <- c(v,model.parts$tm@metrics$totss)
    v_names <- c(v_names,"train")
  }
  if ( valid ) {
    if( is.null(model.parts$vm) ) invisible(.warn.no.validation())
    else {
      v <- c(v,model.parts$vm@metrics$totss)
      v_names <- c(v_names,"valid")
    }
  }
  if ( xval ) {
    if( is.null(model.parts$xm) ) invisible(.warn.no.cross.validation())
    else {
      v <- c(v,model.parts$xm@metrics$totss)
      v_names <- c(v_names,"xval")
    }
  }
  names(v) <- v_names
  if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
}

#'
#' Retrieve the number of iterations.
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "DCAPS"), 
#'                         training_frame = prostate, family = "binomial", 
#'                         nfolds = 0, alpha = 0.5, lambda_search = FALSE)
#' h2o.num_iterations(prostate_glm)
#' }

#' @export
h2o.num_iterations <- function(object) { object@model$model_summary$number_of_iterations }

#'
#' Retrieve centroid statistics
#'
#' Retrieve the centroid statistics.
#' If "train" and "valid" parameters are FALSE (default), then the training centroid stats value is returned. If more
#' than one parameter is set to TRUE, then a named list of centroid stats data frames are returned, where the names are "train" or "valid"
#' For cross validation metrics this statistics are not available.
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
#' @param train Retrieve the training centroid statistics
#' @param valid Retrieve the validation centroid statistics
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' fr <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_train.csv")
#' predictors <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")
#' km <- h2o.kmeans(x = predictors, training_frame = fr, k = 3, nfolds = 3)
#' h2o.centroid_stats(km, train = TRUE)
#' }
#' @export
h2o.centroid_stats <- function(object, train=FALSE, valid=FALSE) {
  model.parts <- .model.parts(object)
  if ( !train && !valid) return( model.parts$tm@metrics$centroid_stats )
  v <- list()
  v_names <- c()
  if ( train ) {
    v[[length(v)+1]] <- model.parts$tm@metrics$centroid_stats
    v_names <- c(v_names,"train")
  }
  if ( valid ) {
    if( is.null(model.parts$vm) ) invisible(.warn.no.validation())
    else {
      v[[length(v)+1]] <- model.parts$vm@metrics$centroid_stats
      v_names <- c(v_names,"valid")
    }
  }
  names(v) <- v_names
  if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
}

#'
#' Retrieve the cluster sizes
#'
#' Retrieve the cluster sizes.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training cluster sizes value is returned. If more
#' than one parameter is set to TRUE, then a named list of cluster size vectors are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
#' @param train Retrieve the training cluster sizes
#' @param valid Retrieve the validation cluster sizes
#' @param xval Retrieve the cross-validation cluster sizes
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' fr <- h2o.importFile("https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_train.csv")
#' predictors <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")
#' km <- h2o.kmeans(x = predictors, training_frame = fr, k = 3, nfolds = 3)
#' h2o.cluster_sizes(km, train = TRUE)
#' }
#' @export
h2o.cluster_sizes <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  model.parts <- .model.parts(object)
  if ( !train && !valid && !xval ) return( model.parts$tm@metrics$centroid_stats$size )
  v <- list()
  v_names <- c()
  if ( train ) {
    v[[length(v)+1]] <- model.parts$tm@metrics$centroid_stats$size
    v_names <- c(v_names,"train")
  }
  if ( valid ) {
    if( is.null(model.parts$vm) ) invisible(.warn.no.validation())
    else {
      v[[length(v)+1]] <- model.parts$vm@metrics$centroid_stats$size
      v_names <- c(v_names,"valid")
    }
  }
  if ( xval ) {
    if( is.null(model.parts$xm) ) invisible(.warn.no.cross.validation())
    else {
      v[[length(v)+1]] <- model.parts$xm@metrics$centroid_stats$size
      v_names <- c(v_names,"xval")
    }
  }
  names(v) <- v_names
  if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
}


#'
#' Retrieve the null deviance
#'
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training null deviance value is returned. If more
#' than one parameter is set to TRUE, then a named vector of null deviances are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} or \linkS4class{H2OModelMetrics}
#' @param train Retrieve the training null deviance
#' @param valid Retrieve the validation null deviance
#' @param xval Retrieve the cross-validation null deviance
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "DCAPS"), 
#'                         training_frame = prostate, family = "binomial", nfolds = 0, 
#'                         alpha = 0.5, lambda_search = FALSE)
#' h2o.null_deviance(prostate_glm, train = TRUE)
#' }
#' @export
h2o.null_deviance <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$null_deviance )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$null_deviance
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$null_deviance)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$null_deviance)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$null_deviance)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No null deviance for ", class(object)))
  invisible(NULL)
}


#' Retrieve the residual deviance
#'
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training residual deviance value is returned. If more
#' than one parameter is set to TRUE, then a named vector of residual deviances are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} or \linkS4class{H2OModelMetrics}
#' @param train Retrieve the training residual deviance
#' @param valid Retrieve the validation residual deviance
#' @param xval Retrieve the cross-validation residual deviance
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "DCAPS"), 
#'                         training_frame = prostate, family = "binomial", 
#'                         nfolds = 0, alpha = 0.5, lambda_search = FALSE)
#' h2o.residual_deviance(prostate_glm, train = TRUE)
#' }
#' @export
h2o.residual_deviance <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$residual_deviance )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$residual_deviance
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$residual_deviance)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$residual_deviance)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$residual_deviance)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No residual deviance for ", class(object)))
  invisible(NULL)
}


#' Retrieve the residual degrees of freedom
#'
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training residual degrees of freedom value is returned. If more
#' than one parameter is set to TRUE, then a named vector of residual degrees of freedom are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} or \linkS4class{H2OModelMetrics}
#' @param train Retrieve the training residual degrees of freedom
#' @param valid Retrieve the validation residual degrees of freedom
#' @param xval Retrieve the cross-validation residual degrees of freedom
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "DCAPS"), 
#'                         training_frame = prostate, family = "binomial", 
#'                         nfolds = 0, alpha = 0.5, lambda_search = FALSE)
#' h2o.residual_dof(prostate_glm, train = TRUE)
#' }
#' @export
h2o.residual_dof <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$residual_degrees_of_freedom )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$residual_degrees_of_freedom
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$residual_degrees_of_freedom)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$residual_degrees_of_freedom)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$residual_degrees_of_freedom)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No residual dof for ", class(object)))
  invisible(NULL)
}


#' Retrieve the null degrees of freedom
#'
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training null degrees of freedom value is returned. If more
#' than one parameter is set to TRUE, then a named vector of null degrees of freedom are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} or \linkS4class{H2OModelMetrics}
#' @param train Retrieve the training null degrees of freedom
#' @param valid Retrieve the validation null degrees of freedom
#' @param xval Retrieve the cross-validation null degrees of freedom
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "DCAPS"), 
#'                         training_frame = prostate, family = "binomial", nfolds = 0, 
#'                         alpha = 0.5, lambda_search = FALSE)
#' h2o.null_dof(prostate_glm, train = TRUE)
#' }
#' @export
h2o.null_dof <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$null_degrees_of_freedom )
  if( is(object, "H2OModel") ) {
    model.parts <- .model.parts(object)
    if ( !train && !valid && !xval ) {
      metric <- model.parts$tm@metrics$null_degrees_of_freedom
      if ( !is.null(metric) ) return(metric)
    }
    v <- c()
    v_names <- c()
    if ( train ) {
      v <- c(v,model.parts$tm@metrics$null_degrees_of_freedom)
      v_names <- c(v_names,"train")
    }
    if ( valid ) {
      if( is.null(model.parts$vm) ) return(invisible(.warn.no.validation()))
      else {
        v <- c(v,model.parts$vm@metrics$null_degrees_of_freedom)
        v_names <- c(v_names,"valid")
      }
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return(invisible(.warn.no.cross.validation()))
      else {
        v <- c(v,model.parts$xm@metrics$null_degrees_of_freedom)
        v_names <- c(v_names,"xval")
      }
    }
    if ( !is.null(v) ) {
      names(v) <- v_names
      if ( length(v)==1 ) { return( v[[1]] ) } else { return( v ) }
    }
  }
  warning(paste0("No null dof for ", class(object)))
  invisible(NULL)
}

#' Access H2O Gains/Lift Tables
#'
#' Retrieve either a single or many Gains/Lift tables from H2O objects.
#'
#' The \linkS4class{H2OModelMetrics} version of this function will only take
#' \linkS4class{H2OBinomialMetrics} objects.
#'
#' @param object Either an \linkS4class{H2OModel} object or an
#'        \linkS4class{H2OModelMetrics} object.
#' @param newdata An H2OFrame object that can be scored on.
#'        Requires a valid response column.
#' @param valid Retrieve the validation metric.
#' @param xval Retrieve the cross-validation metric.
#' @param \dots further arguments to be passed to/from this method.
#' @return Calling this function on \linkS4class{H2OModel} objects returns a
#'         Gains/Lift table corresponding to the \code{\link{predict}} function.
#' @seealso \code{\link{predict}} for generating prediction frames,
#'          \code{\link{h2o.performance}} for creating
#'          \linkS4class{H2OModelMetrics}.
#' @aliases h2o.gains_lift
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, distribution = "bernoulli",
#'                  training_frame = prostate, validation_frame = prostate, nfolds = 3)
#' h2o.gainsLift(model)              ## extract training metrics
#' h2o.gainsLift(model, valid = TRUE)  ## extract validation metrics (here: the same)
#' h2o.gainsLift(model, xval = TRUE)  ## extract cross-validation metrics
#' h2o.gainsLift(model, newdata = prostate) ## score on new data (here: the same)
#' # Generating a ModelMetrics object
#' perf <- h2o.performance(model, prostate)
#' h2o.gainsLift(perf)               ## extract from existing metrics object
#' }
#' @export
setGeneric("h2o.gainsLift", function(object, ...) {})

#' @rdname h2o.gainsLift
#' @export
h2o.gains_lift <- function(object, ...) h2o.gainsLift(object, ...)

#' @rdname h2o.gainsLift
#' @export
setMethod("h2o.gainsLift", "H2OModel", function(object, newdata, valid=FALSE, xval=FALSE,...) {
  model.parts <- .model.parts(object)
  if( missing(newdata) ) {
    if( valid ) {
      if( is.null(model.parts$vm) ) return( invisible(.warn.no.validation()) )
      else                          return( h2o.gainsLift(model.parts$vm, ...) )
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return( invisible(.warn.no.cross.validation()))
      else                          return( h2o.gainsLift(model.parts$xm, ...) )
    }
    return( h2o.gainsLift(model.parts$tm, ...) )
  } else {
    if( valid ) stop("Cannot have both `newdata` and `valid=TRUE`", call.=FALSE)
    if( xval )  stop("Cannot have both `newdata` and `xval=TRUE`", call.=FALSE)
  }


  # ok need to score on the newdata
  url <- paste0("Predictions/models/",object@model_id, "/frames/", h2o.getId(newdata))
  res <- .h2o.__remoteSend(url, method="POST")

  # Make the correct class of metrics object
  metrics <- new(sub("Model", "Metrics", class(object)), algorithm=object@algorithm, metrics= res$model_metrics[[1L]])
  h2o.gainsLift(metrics, ...)
})

#' @rdname h2o.gainsLift
#' @export
setMethod("h2o.gainsLift", "H2OModelMetrics", function(object) {
  if( is(object, "H2OBinomialMetrics") ) {
    return(object@metrics$gains_lift_table)
  } else {
    warning(paste0("No Gains/Lift table for ",class(object)))
    return(NULL)
  }
})

#' Kolmogorov-Smirnov metric for binomial models
#'
#' Retrieves a Kolmogorov-Smirnov metric for given binomial model. The number returned is in range between 0 and 1.
#' K-S metric represents the degree of separation between the positive (1) and negative (0) cumulative distribution
#' functions. Detailed metrics per each group are to be found in the gains-lift table.
#'
#' The \linkS4class{H2OModelMetrics} version of this function will only take
#' \linkS4class{H2OBinomialMetrics} objects.
#'
#' @param object Either an \linkS4class{H2OModel} object or an
#'        \linkS4class{H2OModelMetrics} object.
#' @return Kolmogorov-Smirnov metric, a number between 0 and 1.
#' @seealso \code{\link{h2o.gainsLift}} to see detailed K-S metrics per group
#' 
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' data <- h2o.importFile(
#' path = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
#' model <- h2o.gbm(x = c("Origin", "Distance"), y = "IsDepDelayed", 
#'                        training_frame = data, ntrees = 1)
#' h2o.kolmogorov_smirnov(model)
#' }
#' @export
setGeneric("h2o.kolmogorov_smirnov", function(object) {})

#' @rdname h2o.kolmogorov_smirnov
#' @export
setMethod("h2o.kolmogorov_smirnov", "H2OModelMetrics", function(object) {
  gains_lift <- h2o.gainsLift(object = object)
  if(is.null(gains_lift)){
    warning(paste0("No Gains/Lift table for ",class(object)))
    return(NULL)
  } else {
    return(max(gains_lift$kolmogorov_smirnov))
  }
  
})

#' @rdname h2o.kolmogorov_smirnov
#' @export
setMethod("h2o.kolmogorov_smirnov", "H2OModel", function(object) {
  gains_lift <- h2o.gainsLift(object = object)
  if(is.null(gains_lift)){
    warning(paste0("No Gains/Lift table for ",class(object)))
    return(NULL)
  } else {
    return(max(gains_lift$kolmogorov_smirnov))
  }
  
})


#' Access H2O Confusion Matrices
#'
#' Retrieve either a single or many confusion matrices from H2O objects.
#'
#' The \linkS4class{H2OModelMetrics} version of this function will only take
#' \linkS4class{H2OBinomialMetrics} or \linkS4class{H2OMultinomialMetrics}
#' objects. If no threshold is specified, all possible thresholds are selected.
#'
#' @param object Either an \linkS4class{H2OModel} object or an
#'        \linkS4class{H2OModelMetrics} object.
#' @param newdata An H2OFrame object that can be scored on.
#'        Requires a valid response column.
#' @param thresholds (Optional) A value or a list of valid values between 0.0 and 1.0.
#'        This value is only used in the case of
#'        \linkS4class{H2OBinomialMetrics} objects.
#' @param metrics (Optional) A metric or a list of valid metrics ("min_per_class_accuracy", "absolute_mcc", "tnr", "fnr", "fpr", "tpr", "precision", "accuracy", "f0point5", "f2", "f1").
#'        This value is only used in the case of
#'        \linkS4class{H2OBinomialMetrics} objects.
#' @param valid Retrieve the validation metric.
#' @param xval Retrieve the cross-validation metric.
#' @param ... Extra arguments for extracting train or valid confusion matrices.
#' @return Calling this function on \linkS4class{H2OModel} objects returns a
#'         confusion matrix corresponding to the \code{\link{predict}} function.
#'         If used on an \linkS4class{H2OBinomialMetrics} object, returns a list
#'         of matrices corresponding to the number of thresholds specified.
#' @seealso \code{\link{predict}} for generating prediction frames,
#'          \code{\link{h2o.performance}} for creating
#'          \linkS4class{H2OModelMetrics}.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' h2o.confusionMatrix(model, prostate)
#' # Generating a ModelMetrics object
#' perf <- h2o.performance(model, prostate)
#' h2o.confusionMatrix(perf)
#' }
#' @export
setGeneric("h2o.confusionMatrix", function(object, ...) {})

#' @rdname h2o.confusionMatrix
#' @export
setMethod("h2o.confusionMatrix", "H2OModel", function(object, newdata, valid=FALSE, xval=FALSE,...) {
  model.parts <- .model.parts(object)
  if( missing(newdata) ) {
    if( valid ) {
      if( is.null(model.parts$vm) ) return( invisible(.warn.no.validation()) )
      else                          return( h2o.confusionMatrix(model.parts$vm, ...) )
    } else if( xval ) {
      if( is.null(model.parts$xm) ) return( invisible(.warn.no.cross.validation()) )
      else                          return( h2o.confusionMatrix(model.parts$xm, ...) )
    }
    else {
                                    return( h2o.confusionMatrix(model.parts$tm, ...) )
    }   
  } else {
    if( valid ) stop("Cannot have both `newdata` and `valid=TRUE`", call.=FALSE)
    if( xval ) stop("Cannot have both `newdata` and `xval=TRUE`", call.=FALSE)
  }

  # ok need to score on the newdata
  url <- paste0("Predictions/models/",object@model_id, "/frames/", h2o.getId(newdata))
  res <- .h2o.__remoteSend(url, method="POST")

  # Make the correct class of metrics object
  metrics <- new(sub("Model", "Metrics", class(object)), algorithm=object@algorithm, metrics= res$model_metrics[[1L]])   # FIXME: don't think model metrics come out of Predictions anymore!!!
  h2o.confusionMatrix(metrics, ...)
})

.h2o.metrics_aliases <- list(
    fallout='fpr',
    missrate='fnr',
    recall='tpr',
    sensitivity='tpr',
    specificity='tnr'
)
.h2o.maximizing_metrics <- c('absolute_mcc', 'accuracy', 'precision',
                             'f0point5', 'f1', 'f2',
                             'mean_per_class_accuracy', 'min_per_class_accuracy',
                             'fpr', 'fnr', 'tpr', 'tnr', names(.h2o.metrics_aliases))

#' @rdname h2o.confusionMatrix
#' @export
setMethod("h2o.confusionMatrix", "H2OModelMetrics", function(object, thresholds=NULL, metrics=NULL) {
  if( !is(object, "H2OBinomialMetrics") ) {
    if( is(object, "H2OMultinomialMetrics") ||  is(object, "H2OOrdinalMetrics"))
      return(object@metrics$cm$table)
    warning(paste0("No Confusion Matrices for ",class(object)))
    return(NULL)
  }
  # H2OBinomial case
  if( is.null(metrics) && is.null(thresholds) ) {
    metrics = c("f1")
  }
  if( is(metrics, "list") ) metrics_list = metrics
  else {
    if( is.null(metrics) ) metrics_list = list()
    else metrics_list = list(metrics)
  }
  if( is(thresholds, "list") ) thresholds_list = thresholds
    else {
      if( is.null(thresholds) ) thresholds_list = list()
      else thresholds_list = as.list(thresholds)
  }

  # error check the metrics_list and thresholds_list
  if( !all(sapply(thresholds_list, f <- function(x) is.numeric(x) && x >= 0 && x <= 1)) )
    stop("All thresholds must be numbers between 0 and 1 (inclusive).")
  if( !all(sapply(metrics_list, f <- function(x) x %in% .h2o.maximizing_metrics)) )
      stop(paste("The only allowable metrics are ", paste(.h2o.maximizing_metrics, collapse=', ')))

  # make one big list that combines the thresholds and metric-thresholds
  metrics_thresholds = lapply(metrics_list, f <- function(x) h2o.find_threshold_by_max_metric(object, x))
  thresholds_list <- append(thresholds_list, metrics_thresholds)
  first_metrics_thresholds_offset <- length(thresholds_list) - length(metrics_thresholds)

  thresh2d <- object@metrics$thresholds_and_metric_scores
  actual_thresholds <- thresh2d$threshold
  d <- object@metrics$domain
  m <- lapply(seq_along(thresholds_list), function(i) {
    t <- thresholds_list[[i]]
    row <- h2o.find_row_by_threshold(object,t)
    if( is.null(row) ) NULL
    else {
      tns <- row$tns; fps <- row$fps; fns <- row$fns; tps <- row$tps;
      rnames <- c(d, "Totals")
      cnames <- c(d, "Error", "Rate")
      col1 <- c(tns, fns, tns+fns)
      col2 <- c(fps, tps, fps+tps)
      col3 <- c(fps/(fps+tns), fns/(fns+tps), (fps+fns)/(fps+tns+fns+tps))
      col4 <- c( paste0(" =", fps, "/", fps+tns), paste0(" =", fns, "/", fns+tps), paste0(" =", fns+fps, "/", fps+tns+fns+tps) )
      fmts <- c("%i", "%i", "%f", "%s")
      tbl <- data.frame(col1,col2,col3,col4)
      colnames(tbl) <- cnames
      rownames(tbl) <- rnames
      header <-  "Confusion Matrix (vertical: actual; across: predicted) "
      if(t %in% metrics_thresholds) {
        m <- metrics_list[i - first_metrics_thresholds_offset]
        if( length(m) > 1) m <- m[[1]]
        header <- paste(header, "for max", m, "@ threshold =", t)
      } else {
        header <- paste(header, "@ threshold =", row$threshold)
      }
      attr(tbl, "header") <- header
      attr(tbl, "formats") <- fmts
      oldClass(tbl) <- c("H2OTable", "data.frame")
      tbl
    }
  })
  if( length(m) == 1L ) return( m[[1L]] )
  m
})

#' Plot an H2O Model
#'
#' Plots training set (and validation set if available) scoring history for an H2O Model
#'
#' This method dispatches on the type of H2O model to select the correct
#' scoring history.  The \code{timestep} and \code{metric} arguments are restricted to what is
#' available in the scoring history for a particular type of model.
#'
#' @param x A fitted \linkS4class{H2OModel} object for which the scoring history plot is desired.
#' @param timestep A unit of measurement for the x-axis.
#' @param metric A unit of measurement for the y-axis.
#' @param ... additional arguments to pass on.
#' @return Returns a scoring history plot.
#' @seealso \code{\link{h2o.deeplearning}}, \code{\link{h2o.gbm}},
#'          \code{\link{h2o.glm}}, \code{\link{h2o.randomForest}} for model
#'          generation in h2o.
#' @examples
#' \dontrun{
#' if (requireNamespace("mlbench", quietly=TRUE)) {
#'     library(h2o)
#'     h2o.init()
#'
#'     df <- as.h2o(mlbench::mlbench.friedman1(10000, 1))
#'     rng <- h2o.runif(df, seed = 1234)
#'     train <- df[rng < 0.8,]
#'     valid <- df[rng >= 0.8,]
#'
#'     gbm <- h2o.gbm(x = 1:10, y = "y", training_frame = train, validation_frame = valid,
#'                    ntrees = 500, learn_rate = 0.01, score_each_iteration = TRUE)
#'     plot(gbm)
#'     plot(gbm, timestep = "duration", metric = "deviance")
#'     plot(gbm, timestep = "number_of_trees", metric = "deviance")
#'     plot(gbm, timestep = "number_of_trees", metric = "rmse")
#'     plot(gbm, timestep = "number_of_trees", metric = "mae")
#' }
#' }
#' @method plot H2OModel
#' @export
plot.H2OModel <- function(x, timestep = "AUTO", metric = "AUTO", ...) {
  df <- as.data.frame(x@model$scoring_history)

  #Ensure metric and timestep can be passed in as upper case (by converting to lower case) if not "AUTO"
  if(metric != "AUTO"){
    metric <- tolower(metric)
  }

  if(timestep != "AUTO"){
    timestep <- tolower(timestep)
  }

  # Separate functionality for GLM since output is different from other algos
  if (x@algorithm %in% c("gam", "glm")) {
    if ("gam" == x@algorithm)
      df <- as.data.frame(x@model$glm_scoring_history)
    if (x@allparameters$lambda_search) {
      allowed_metrics <- c("deviance_train", "deviance_test", "deviance_xval")
      allowed_timesteps <- c("iteration", "duration")
      df <- df[df["alpha"] == x@model$alpha_best,]
    } else if (!is.null(x@allparameters$HGLM) && x@allparameters$HGLM) {
      allowed_metrics <- c("convergence", "sumetaieta02")
      allowed_timesteps <- c("iterations", "duration")
    } else {
      allowed_metrics <- c("objective", "negative_log_likelihood")
      allowed_timesteps <- c("iterations", "duration")
    }

    if (timestep == "AUTO") {
      timestep <- allowed_timesteps[[1]]
    } else if (!(metric %in% allowed_timesteps)) {
      stop("for ", toupper(x@algorithm), ", timestep must be one of: ", paste(allowed_timesteps, collapse = ", "))
    }

    if (metric == "AUTO") {
      metric <- allowed_metrics[[1]]
    } else if (!(metric %in% allowed_metrics)) {
      stop("for ", toupper(x@algorithm),", metric must be one of: ", paste(allowed_metrics, collapse = ", "))
    }

    graphics::plot(df$iteration, df[, c(metric)], type="l", xlab = timestep, ylab = metric, main = "Validation Scoring History", ...)
  } else if (x@algorithm == "glrm") {
    timestep <- "iteration"
    if (metric == "AUTO") {
      metric <- "objective"
    } else if (!(metric %in% c("step_size", "objective"))) {
      stop("for GLRM, metric must be one of: step_size, objective")
    }
    graphics::plot(df$iteration, df[,c(metric)], type="l", xlab = timestep, ylab = metric, main = "Objective Function Value per Iteration", ...)
  } else if (x@algorithm %in% c("deeplearning", "drf", "gbm")) {
    if (is(x, "H2OBinomialModel")) {
      if (metric == "AUTO") {
        metric <- "logloss"
      } else if (!(metric %in% c("logloss","auc","classification_error","rmse"))) {
        stop("metric for H2OBinomialModel must be one of: logloss, auc, classification_error, rmse")
      }
    } else if (is(x, "H2OMultinomialModel") || is(x, "H2OOrdinalModel")) {
      if (metric == "AUTO") {
        metric <- "classification_error"
      } else if (!(metric %in% c("logloss","classification_error","rmse"))) {
        stop("metric for H2OMultinomialModel/H2OOrdinalModel must be one of: logloss, classification_error, rmse")
      }
    } else if (is(x, "H2ORegressionModel")) {
      if (metric == "AUTO") {
        metric <- "rmse"
      } else if (!(metric %in% c("rmse","deviance","mae"))) {
        stop("metric for H2ORegressionModel must be one of: rmse, mae, or deviance")
      }
    } else {
      stop("Must be one of: H2OBinomialModel, H2OMultinomialModel, H2OOrdinalModel or H2ORegressionModel")
    }
    # Set timestep
    if (x@algorithm %in% c("gbm", "drf")) {
      if (timestep == "AUTO") {
        timestep <- "number_of_trees"
      } else if (!(timestep %in% c("duration","number_of_trees"))) {
        stop("timestep for gbm or drf must be one of: duration, number_of_trees")
      }
    } else { # x@algorithm == "deeplearning"
      # Delete first row of DL scoring history since it contains NAs & NaNs
      if (df$samples[1] == 0) {
        df <- df[-1,]
      }
      if (timestep == "AUTO") {
        timestep <- "epochs"
      } else if (!(timestep %in% c("epochs","samples","duration"))) {
        stop("timestep for deeplearning must be one of: epochs, samples, duration")
             }
    }
    training_metric <- sprintf("training_%s", metric)
    validation_metric <- sprintf("validation_%s", metric)
    if (timestep == "duration") {
      trim <- function (ss) gsub("^\\s+|\\s+$", "", ss)
      tt <- trim(df[2, c("duration")])  #base::trimws not implemented for earlier versions of R, so we make our own trim function
      dur_colname <- sprintf("duration_%s", strsplit(tt, " ")[[1]][2]) #parse units of measurement
      df[,c(dur_colname)] <- apply(as.matrix(df[,c("duration")]), 1, function(v) as.numeric(strsplit(trim(v), " ")[[1]][1]))
      timestep <- dur_colname
    }
    if (validation_metric %in% names(df)) {  #Training and Validation scoring history
      ylim <- range(c(df[,c(training_metric)], df[,c(validation_metric)]))  #sync up y axes
      if (sum(is.na(ylim))>1) {
        ylim <- c(0.0, 1.0)
      }
      graphics::plot(df[,c(timestep)], df[,c(training_metric)], type="l", xlab = "", ylab = "", axes = FALSE,
                     main = "Scoring History", col = "blue", ylim = ylim, ...)
      graphics::par(new = TRUE)
      graphics::plot(df[,c(timestep)], df[,c(validation_metric)], type="l", xlab = timestep, ylab = metric, col = "orange", ylim = ylim, ...)
      graphics::legend("topright", legend = c("Training", "Validation"), col = c("blue", "orange"), lty = c(1,1))
    } else {  #Training scoring history only
      ylim <- range(c(df[,c(training_metric)]))
      if (sum(is.na(ylim))>1) {
        ylim <- c(0.0, 1.0)
      }
      graphics::plot(df[,c(timestep)], df[,c(training_metric)], type="l", xlab = timestep, ylab = training_metric,
                     main = "Training Scoring History", col = "blue", ylim = ylim)

    }
  } else { # algo is not glm, deeplearning, drf, gbm
  	stop("Plotting not implemented for this type of model")
  }
}

#' Plot Variable Importances
#'
# Plot a trained model's variable importances.
#'
#' @param model A trained model (accepts a trained random forest, GBM,
#' or deep learning model, will use \code{\link{h2o.std_coef_plot}}
#' for a trained GLM
#' @param num_of_features The number of features shown in the plot (default is 10 or all if less than 10).
#' @seealso \code{\link{h2o.std_coef_plot}} for GLM.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = prostate, distribution = "bernoulli")
#' h2o.varimp_plot(model)
#'
#' # for deep learning set the variable_importance parameter to TRUE
#' iris_hf <- as.h2o(iris)
#' iris_dl <- h2o.deeplearning(x = 1:4, y = 5, training_frame = iris_hf,
#' variable_importances = TRUE)
#' h2o.varimp_plot(iris_dl)
#' }
#' @export
h2o.varimp_plot <- function(model, num_of_features = NULL){
  # store the variable importance table as vi
  vi <- h2o.varimp(model)

  # check if num_of_features was passed as an integer, otherwise use all features
  # default to 10 or less features if num_of_features is not specified
  #  if(is.null(num_of_features)) {num_of_features = length(vi$variable)}
  #  else if ((num_of_features != round(num_of_features)) || (num_of_features <= 0)) stop("num_of_features must be an integer greater than 0")
  if(is.null(num_of_features)) {
    feature_count = length(vi$variable)
    num_of_features = ifelse(feature_count <= 10, length(vi$variable), 10)
  } else if ((num_of_features != round(num_of_features)) || (num_of_features <= 0)) stop("num_of_features must be an integer greater than 0")

  # check the model type and then update the model title
  if(model@algorithm[1] == "deeplearning") {title = "Variable Importance: Deep Learning"}
  else {title = paste("Variable Importance: ", model_type = toupper(model@algorithm[1]), sep="")}

  # use the longest ylable to adjust margins so ylabels don't cut off long string labels
  ylabels = vi$variable
  ymargin <-  max(strwidth(ylabels, "inch")+0.4, na.rm = TRUE)
  par(mai=c(1.02,ymargin,0.82,0.42))

  # if num_of_features = 1, creat only one bar (adjust size to look nice)
  if(num_of_features == 1) {
    barplot(rev(head(vi$scaled_importance, n = num_of_features)),
            names.arg = rev(head(vi$variable, n = num_of_features)),
            width = 0.2,
            space = 1,
            horiz = TRUE, las = 2,
            ylim=c(0 ,2),
            xlim = c(0,1),
            axes = TRUE,
            col ='#1F77B4',
            main = title)
  }

  # plot num_of_features > 1
  else if (num_of_features > 1) {
    barplot(rev(head(vi$scaled_importance, n = num_of_features)),
            names.arg = rev(head(vi$variable, n = num_of_features)),
            space = 1,las = 2,
            horiz = TRUE,
            col ='#1F77B4', # blue
            main = title)
  }
}

#' Plot Standardized Coefficient Magnitudes
#'
#' Plot a GLM model's standardized coefficient magnitudes.
#'
#' @param model A trained generalized linear model
#' @param num_of_features The number of features to be shown in the plot
#' @seealso \code{\link{h2o.varimp_plot}} for variable importances plot of
#'          random forest, GBM, deep learning.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#'
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "DCAPS"),
#'                          training_frame = prostate, family = "binomial",
#'                          nfolds = 0, alpha = 0.5, lambda_search = FALSE)
#' h2o.std_coef_plot(prostate_glm)
#' }
#' @export
h2o.std_coef_plot <- function(model, num_of_features = NULL){
  # check that the model is a glm
  if(model@algorithm[1] != "glm") stop("Warning: model must be a GLM")
  maxcoeff = 1
  if (model@model$model_summary["family"]=="multinomial") {
    coeff_table <- model@model$standardized_coefficient_magnitudes
    sorted_table <- coeff_table[order(abs(coeff_table$coefficients)),]
    norm_coef <- sorted_table$coefficients
    sort_norm <- norm_coef
    maxcoeff = max(norm_coef)
  } else {
  # get the coefficients table
  coeff_table_complete <- model@model$coefficients_table

  # remove the intercept row from the complete coeff_table_complete
  coeff_table <- coeff_table_complete[coeff_table_complete$names != "Intercept",]
  # order the coeffcients table by the absolute value of the standardized_coefficients
  sorted_table <- coeff_table[order(abs(coeff_table$standardized_coefficients)),]

  # get a vector of normalized coefs. and abs norm coefs., and the corresponding labels
  norm_coef <- sorted_table$standardized_coefficients
  sort_norm <- abs(sorted_table$standardized_coefficients)
}
  labels <- sorted_table$names
  

  # check if num_of_features was passed as an integer, otherwise use all features
  if(is.null(num_of_features)) {num_of_features = length(norm_coef)}
  else if ((num_of_features != round(num_of_features)) || (num_of_features <= 0)) stop("num_of_features must be an integer greater than 0")

  # initialize a vector of color codes, based on norm_coef values
  color_code <- c()
  for(element in norm_coef)
  {if(element >= 0) color_code <- append(color_code, "#1F77B4")  # blue
  else color_code <- append(color_code, '#FF7F0E')} # orange

  # get the color sign, needed for the legend
  color_sign <- c()
  for(element in norm_coef)
  {if(element >= 0) color_sign <- append(color_sign, "Positive")  # blue
  else color_sign <- append(color_sign, 'Negative')} # orange

  # use the longest ylable to adjust margins so ylabels don't cut off long string labels
  ylabels = labels
  ymargin <-  max(strwidth(ylabels, "inch")+0.4, na.rm = TRUE)
  par(mai=c(1.02,ymargin,0.82,0.42))

  # check if num_of_features = 1 and plot only one bar
  if(num_of_features == 1) {
    barplot(rev(sort_norm)[num_of_features],
            names.arg = rev(labels)[num_of_features],
            width = 0.2,
            space = 1,
            horiz = TRUE, las = 1,
            ylim=c(0 ,2),
            xlim = c(0,maxcoeff),
            col = rev(color_code)[num_of_features],
            main = "Standardized Coef. Magnitudes")
  }

  # create horizontal barplot for one or more features
  else {
    barplot(tail(sort_norm, n = num_of_features),
        names.arg = tail(labels, n = num_of_features),
        legend.text = TRUE,
        space = 1,
        horiz = TRUE, las = 1,
        col = tail(color_code, n = num_of_features),
        xlim = c(0,maxcoeff),
        main = "Standardized Coef. Magnitudes")
  }

  # add legend, that adapts if one to all bars are plotted
  legend('bottomright', legend = unique(tail(color_sign, n = num_of_features)),
  col = unique(tail(color_code, n = num_of_features)), pch = 20)

}

#' Plot Gains/Lift curves
#' @param object Either an H2OModel or H2OModelMetrics
#' @param type What curve to plot. One of "both", "gains", "lift".
#' @param ... Optional arguments
#'
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' data <- h2o.importFile(
#' path = "https://s3.amazonaws.com/h2o-public-test-data/smalldata/airlines/allyears2k_headers.zip")
#' model <- h2o.gbm(x = c("Origin", "Distance"), y = "IsDepDelayed", training_frame = data, ntrees = 1)
#' h2o.gains_lift_plot(model)
#' }
#' @export
setGeneric("h2o.gains_lift_plot", function(object, type = c("both", "gains", "lift"), ...) {})

.gains_lift_plot <- function(gain_table, type) {
  labels <- character()
  colors <- character()

  if (type == "both") {
    ylim <- c(0, max(gain_table$cumulative_capture_rate, gain_table$cumulative_lift))
    ylab <- "cumulative capture rate, cumulative lift"
    title <- "Gains / Lift"
  } else if (type == "gains") {
    ylim <- c(0, max(gain_table$cumulative_capture_rate))
    ylab <- "cumulative capture rate"
    title <- "Gains"
  } else if (type == "lift") {
    ylim <- c(0, max(gain_table$cumulative_lift))
    ylab <- "cumulative lift"
    title <- "Lift"
  }
  if (type %in% c("both", "gains")) {
    graphics::plot(gain_table$cumulative_data_fraction,
                   gain_table$cumulative_capture_rate,
                   type='l',
                   ylim = ylim,
                   col = "blue",
                   xlab = "cumulative data fraction",
                   ylab = ylab,
                   main = title,
                   panel.first = grid())
    labels <- c("cummulative capture rate")
    colors <- c("blue")
  }
  if (type %in% c("both", "lift")) {
    opar <- par(new = type == "both")  # if new == T => don't clean the plot
    on.exit(par(opar))
    graphics::plot(gain_table$cumulative_data_fraction,
                   gain_table$cumulative_lift,
                   type = "l",
                   ylim = ylim,
                   col = "orange",
                   xlab = "cumulative data fraction",
                   ylab = ylab,
                   main = title,
                   panel.first = grid())
    labels <- c(labels, "cummulative lift")
    colors <- c(colors, "orange")
  }
  legend("topright", labels, lty = 1, col = colors)
}

#' Plot Gains/Lift curves
#' @param object H2OModelMetrics object
#' @param type What curve to plot. One of "both", "gains", "lift".
#' @export
setMethod("h2o.gains_lift_plot", "H2OModelMetrics", function(object, type = c("both", "gains", "lift")) {
  gain_table <- h2o.gainsLift(object)
  .gains_lift_plot(gain_table, type = match.arg(type))
})

#' Plot Gains/Lift curves
#' @param object H2OModel object
#' @param type What curve to plot. One of "both", "gains", "lift".
#' @param xval if TRUE, use cross-validation metrics
#' @export
setMethod("h2o.gains_lift_plot", "H2OModel", function(object, type = c("both", "gains", "lift"), xval = FALSE) {
  gain_table <- h2o.gainsLift(object, xval = xval)
  .gains_lift_plot(gain_table, type = match.arg(type))
})

#' @method plot H2OBinomialMetrics
#' @export
plot.H2OBinomialMetrics <- function(x, type = "roc", main, ...) {
  # TODO: add more types (i.e. cutoffs)
  if(!type %in% c("roc", "pr", "gains_lift")) stop("type must be 'roc', 'pr', or 'gains_lift'")
  if(type == "roc") {
    xaxis <- "False Positive Rate (FPR)"; yaxis = "True Positive Rate (TPR)"
    if(missing(main)) {
      main <- "Receiver Operating Characteristic curve"
      if(x@on_train) {
        main <- paste(main, "(on train)")
      } else if (x@on_valid) {
        main <- paste(main, "(on valid)")
      }
    }
    xdata <- x@metrics$thresholds_and_metric_scores$fpr
    ydata <- x@metrics$thresholds_and_metric_scores$tpr
    graphics::plot(xdata, ydata, main = main, xlab = xaxis, ylab = yaxis, ylim=c(0,1), xlim=c(0,1), type='l', lty=2, col='blue', lwd=2, panel.first = grid())
    graphics::abline(0, 1, lty = 2)
  } else if(type=="pr"){
    xaxis <- "Recall (TP/(TP+FP))"; yaxis = "Precision (TPR)"
    if(missing(main)) {
      main <- "Precision Recall curve"
      if(x@on_train) {
        main <- paste(main, "(on train)")
      } else if (x@on_valid) {
        main <- paste(main, "(on valid)")
      }
    }
    xdata <- rev(x@metrics$thresholds_and_metric_scores$recall)
    ydata <- rev(x@metrics$thresholds_and_metric_scores$precision)
    graphics::plot(xdata, ydata, main = main, xlab = xaxis, ylab = yaxis, ylim=c(0,1), xlim=c(0,1), type='l', lty=2, col='blue', lwd=2, panel.first = grid())
  } else if (type == "gains_lift") {
    h2o.gains_lift_plot(x, ...)
  }
}

#' @method plot H2OBinomialUpliftMetrics
#' @export
plot.H2OBinomialUpliftMetrics <- function(x, metric="AUTO", normalize=FALSE, main, ...) {
    if(!metric %in% c("AUTO", "qini", "lift", "gain")) stop("metric must be 'AUTO', 'qini' or 'lift' or 'gain'")
    if (metric == "AUTO") metric = "qini"
    xaxis <- "Number Targeted"; yaxis = paste("Cumulative", metric)
    if(missing(main)) {
        if(normalize){
          main <- paste("Cumulative Uplift Curve normalized - ", metric)
        } else {
          main <- paste("Cumulative Uplift Curve - ", metric)
        }
        if(x@on_train) {
            main <- paste(main, "(on train)")
        } else if (x@on_valid) {
            main <- paste(main, "(on valid)")
        }
    }
    if(normalize){
      metric.auuc <- h2o.auuc_normalized(x, metric)
      ydata <- eval(parse(text=paste("x@metrics$thresholds_and_metric_scores$", metric, "_normalized", sep="")))
      main <- paste(main, "\nAUUC normalized =", metric.auuc)  
    } else {
      metric.auuc <- h2o.auuc(x, metric)
      ydata <- eval(parse(text=paste("x@metrics$thresholds_and_metric_scores$", metric, sep="")))
      main <- paste(main, "\nAUUC=", metric.auuc)
    }
    xdata <- x@metrics$thresholds_and_metric_scores$n
    a <- ydata[length(ydata)-1] / xdata[length(xdata)-1]
    yrnd <- xdata * a
    graphics::plot(xdata, ydata, main = main, xlab = xaxis, ylab = yaxis, ylim=c(min(ydata, 0),max(ydata)), xlim=c(min(xdata),max(xdata)), type='l', lty=1, col='blue', lwd=2, panel.first = grid())
    graphics::lines(xdata, yrnd, main = main, xlab = xaxis, ylab = yaxis, ylim=c(min(yrnd, 0),max(yrnd)), xlim=c(min(xdata),max(xdata)), type='l', lty=2, col='black', lwd=2, panel.first = grid())        
    if(metric == 'lift') {
        legend("topright", legend=c(metric, "random"), col=c("blue", "black"), inset=.02, lty=1:2, cex=0.8)  
    } else {
        legend("bottomright", legend=c(metric, "random"), col=c("blue", "black"), inset=.02, lty=1:2, cex=0.8)  
    }
}

#' @export
screeplot.H2ODimReductionModel <- function(x, npcs, type = "barplot", main, ...) {
    if(x@algorithm != "pca") stop("x must be an H2O PCA model")
    if(missing(npcs))
      npcs = min(10, x@model$parameters$k)
    else if(!is.numeric(npcs) || npcs < 1 || npcs > x@model$parameters$k)
      stop(paste("npcs must be a positive integer between 1 and", x@model$parameters$k, "inclusive"))

    sdevH2O <- h2o.sdev(x)
    if(missing(main))
      main = paste("h2o.prcomp(", strtrim(x@parameters$training_frame, 20), ")", sep="")
    if(type == "barplot")
      barplot(sdevH2O[1:npcs]^2, main = main, ylab = "Variances", ...)
    else if(type == "lines")
      lines(sdevH2O[1:npcs]^2, main = main, ylab = "Variances", ...)
    else
      stop("type must be either 'barplot' or 'lines'")
}

#'
#' Retrieve the standard deviations of principal components
#'
#' @param object An \linkS4class{H2ODimReductionModel} object.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' cars_pca <- h2o.prcomp(cars, transform = "STANDARDIZE", 
#'                        k = 3, x = predictors, seed = 12345)
#' h2o.sdev(cars_pca)
#' }
#' @export
h2o.sdev <- function(object) {
  if(!is(object, "H2ODimReductionModel") || object@algorithm != "pca")
    stop("object must be an H2O PCA model")
  as.numeric(object@model$importance[1,])
}

# extract "bite size" pieces from a model
.model.parts <- function(object) {
  o  <- object
  m  <- object@model
  tm <- object@model$training_metrics
  vm <- object@model$validation_metrics
  xm <- object@model$cross_validation_metrics
  xms <- object@model$cross_validation_metrics_summary
  if( !is.null(vm@metrics) && !is.null(xm@metrics) ) return( list(o=o,m=m,tm=tm,vm=  vm,xm=  xm,xms=xms) )
  if(  is.null(vm@metrics) && !is.null(xm@metrics) ) return( list(o=o,m=m,tm=tm,vm=NULL,xm=  xm,xms=xms) )
  if( !is.null(vm@metrics) &&  is.null(xm@metrics) ) return( list(o=o,m=m,tm=tm,vm=  vm,xm=NULL,xms=NULL) )
  return( list(o=o,m=m,tm=tm,vm=NULL,xm=NULL,xms=NULL) )
}

.warn.no.validation <- function() {
  warning("No validation metrics available.", call.=FALSE)
  NULL
}

.warn.no.cross.validation <- function() {
  warning("No cross-validation metrics available.", call.=FALSE)
  NULL
}

.isSupervised <- function(algo, params) {
  if (algo == "kmeans" ||
      algo == "glrm" ||
      algo == "pca" ||
      algo == "extendedisolationforest" ||
      (algo == "deeplearning" && !is.null(params$autoencoder) && params$autoencoder)) {
    FALSE
  } else {
    TRUE
  }
}

# Transform given name to
# expected values ("gbm", "drf")
# It allows for having algorithm name aliases
.h2o.unifyAlgoName <- function(algo) {
  result <- if (algo == "randomForest") "drf" else algo
  result
}

#
# Returns REST API version for given algo.
#
.h2o.getAlgoVersion <- function(algo, h2oRestApiVersion = .h2o.__REST_API_VERSION) {
  result <- .h2o.__remoteSend(method = "GET", h2oRestApiVersion = h2oRestApiVersion, .h2o.__MODEL_BUILDERS(algo))$model_builders[[algo]][["__meta"]]$schema_version
  result
}

#' Tabulation between Two Columns of an H2OFrame
#'
#' Simple Co-Occurrence based tabulation of X vs Y, where X and Y are two Vecs in a given dataset.
#' Uses histogram of given resolution in X and Y.
#' Handles numerical/categorical data and missing values. Supports observation weights.
#'
#' @param data An H2OFrame object.
#' @param x predictor column
#' @param y response column
#' @param weights_column (optional) observation weights column
#' @param nbins_x number of bins for predictor column
#' @param nbins_y number of bins for response column
#' @return Returns two TwoDimTables of 3 columns each
#'        count_table:    X     Y counts
#'        response_table: X meanY counts
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' df <- as.h2o(iris)
#' tab <- h2o.tabulate(data = df, x = "Sepal.Length", y = "Petal.Width",
#'              weights_column = NULL, nbins_x = 10, nbins_y = 10)
#' plot(tab)
#' }
#' @export
h2o.tabulate <- function(data, x, y,
                         weights_column = NULL,
                         nbins_x = 50,
                         nbins_y = 50
                         ) {
  args <- .verify_datacols(data, c(x,y))
  if(!is.numeric(nbins_x)) stop("`nbins_x` must be a positive number")
  if(!is.numeric(nbins_y)) stop("`nbins_y` must be a positive number")

  parms = list()
  parms$dataset <- attr(data, "id")
  parms$predictor <- args$cols[1]
  parms$response <- args$cols[2]
  if( !missing(weights_column) )            parms$weight <- weights_column
  parms$nbins_predictor <- nbins_x
  parms$nbins_response <- nbins_y

  res <- .h2o.__remoteSend(method = "POST", h2oRestApiVersion = 99, page = "Tabulate", .params = parms)
  count_table <- res$count_table
  response_table <- res$response_table
  out <- list(count_table = count_table, response_table = response_table, cols = args$cols)
  oldClass(out) <- c("H2OTabulate", "list")
  out
}

#' Plot an H2O Tabulate Heatmap
#'
#' Plots the simple co-occurrence based tabulation of X vs Y as a heatmap, where X and Y are two Vecs in a given dataset. This function requires suggested ggplot2 package.
#'
#' @param x An H2OTabulate object for which the heatmap plot is desired.
#' @param xlab A title for the x-axis.  Defaults to what is specified in the given H2OTabulate object.
#' @param ylab A title for the y-axis.  Defaults to what is specified in the given H2OTabulate object.
#' @param base_size  Base font size for plot.
#' @param ... additional arguments to pass on.
#' @return Returns a ggplot2-based heatmap of co-occurance.
#' @seealso \code{\link{h2o.tabulate}}
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' df <- as.h2o(iris)
#' tab <- h2o.tabulate(data = df, x = "Sepal.Length", y = "Petal.Width",
#'              weights_column = NULL, nbins_x = 10, nbins_y = 10)
#' plot(tab)
#' }
#' @method plot H2OTabulate
#' @export
plot.H2OTabulate <- function(x, xlab = x$cols[1], ylab = x$cols[2], base_size = 12, ...) {

  if (!inherits(x, "H2OTabulate")) {
    stop("Must be an H2OTabulate object")
  }

  if (!requireNamespace("ggplot2", quietly = TRUE)) {
    stop("In order to plot.H2OTabulate you must have ggplot2 package installed")
  }

  # Pull small counts table into R memory to plot
  df <- as.data.frame(x$count_table)
  names(df) <- c("c1", "c2", "counts")

  # Reorder the levels for better plotting
  if (suppressWarnings(is.na(sum(as.numeric(df$c1))))) {
    c1_order <- order(unique(df$c1))
  } else {
    c1_order <- order(unique(as.numeric(df$c1)))
  }
  if (suppressWarnings(is.na(sum(as.numeric(df$c2))))) {
    c2_order <- order(unique(df$c2))
  } else {
    c2_order <- order(unique(as.numeric(df$c2)))
  }
  c1_labels <- unique(df$c1)
  c2_labels <- unique(df$c2)
  df$c1 <- factor(df$c1, levels = c1_labels[c1_order])
  df$c2 <- factor(df$c2, levels = c2_labels[c2_order])

  # Plot heatmap
  c1 <- c2 <- counts <- NULL #set these to pass CRAN checks w/o warnings
  (p <- ggplot2::ggplot(df, ggplot2::aes(c1, c2))
  + ggplot2::geom_tile(ggplot2::aes(fill = counts), colour = "white") + ggplot2::scale_fill_gradient(low = "white", high = "steelblue"))

  # Adjust the plot
  p <- p + ggplot2::theme_grey(base_size = base_size) + ggplot2::labs(x = xlab, y = ylab) + ggplot2::scale_x_discrete(expand = c(0, 0)) + ggplot2::scale_y_discrete(expand = c(0, 0)) + ggplot2::theme(legend.position = "none", axis.ticks = ggplot2::element_blank(), axis.text.x = ggplot2::element_text(size = base_size * 0.8, angle = 330, hjust = 0, colour = "grey50"))

  # Return a ggplot object
  return(p)
}

#'
#' Retrieve the cross-validation models
#'
#' @param object An \linkS4class{H2OModel} object.
#' @return Returns a list of H2OModel objects
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "economy_20mpg"
#' cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, 
#'                     nfolds = 5,  keep_cross_validation_models = TRUE, seed = 1234)
#' h2o.cross_validation_models(cars_gbm)
#' }
#' @export
h2o.cross_validation_models <- function(object) {
  if(!is(object, "H2OModel"))
    stop("object must be an H2O model")
  if (is.null(object@model$cross_validation_models)) return(NULL)
  lapply(object@model$cross_validation_models, function(x) h2o.getModel(x$name))
}

#'
#' Retrieve the cross-validation fold assignment
#'
#' @param object An \linkS4class{H2OModel} object.
#' @return Returns a H2OFrame
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "economy_20mpg"
#' cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train,
#'                     nfolds = 5,  keep_cross_validation_fold_assignment = TRUE, seed = 1234)
#' h2o.cross_validation_fold_assignment(cars_gbm)
#' }
#' @export
h2o.cross_validation_fold_assignment <- function(object) {
  if(!is(object, "H2OModel"))
    stop("object must be an H2O model")
  if (is.null(object@model$cross_validation_fold_assignment)) return(NULL)
  h2o.getFrame(object@model$cross_validation_fold_assignment$name)
}

#'
#' Retrieve the cross-validation holdout predictions
#'
#' @param object An \linkS4class{H2OModel} object.
#' @return Returns a H2OFrame
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])
#' predictors <- c("displacement","power","weight","acceleration","year")
#' response <- "economy_20mpg"
#' cars_split <- h2o.splitFrame(data = cars,ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, 
#'                     nfolds = 5,  keep_cross_validation_predictions = TRUE, seed = 1234)
#' h2o.cross_validation_holdout_predictions(cars_gbm)
#' }
#' @export
h2o.cross_validation_holdout_predictions <- function(object) {
  if(!is(object, "H2OModel"))
    stop("object must be an H2O model")
  if (is.null(object@model$cross_validation_holdout_predictions)) return(NULL)
  h2o.getFrame(object@model$cross_validation_holdout_predictions$name)
}

#'
#' Retrieve the cross-validation predictions
#'
#' @param object An \linkS4class{H2OModel} object.
#' @return Returns a list of H2OFrame objects
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://s3.amazonaws.com/h2o-public-test-data/smalldata/junit/cars_20mpg.csv"
#' cars <- h2o.importFile(f)
#' cars["economy_20mpg"] <- as.factor(cars["economy_20mpg"])
#' predictors <- c("displacement", "power", "weight", "acceleration", "year")
#' response <- "economy_20mpg"
#' cars_split <- h2o.splitFrame(data = cars, ratios = 0.8, seed = 1234)
#' train <- cars_split[[1]]
#' valid <- cars_split[[2]]
#' cars_gbm <- h2o.gbm(x = predictors, y = response, training_frame = train, 
#'                     nfolds = 5,  keep_cross_validation_predictions = TRUE, seed = 1234)
#' h2o.cross_validation_predictions(cars_gbm)
#' }
#' @export
h2o.cross_validation_predictions <- function(object) {
  if(!is(object, "H2OModel"))
    stop("object must be an H2O model")
  if (is.null(object@model$cross_validation_predictions)) return(NULL)
  lapply(object@model$cross_validation_predictions, function(x) h2o.getFrame(x$name))
}

#' Partial Dependence Plots
#'
#' Partial dependence plot gives a graphical depiction of the marginal effect of a variable on the response. The effect
#' of a variable is measured in change in the mean response. Note: Unlike randomForest's partialPlot when plotting
#' partial dependence the mean response (probabilities) is returned rather than the mean of the log class probability.
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param newdata An H2OFrame object used for scoring and constructing the plot.
#' @param cols Feature(s) for which partial dependence will be calculated.
#' @param destination_key An key reference to the created partial dependence tables in H2O.
#' @param nbins Number of bins used. For categorical columns make sure the number of bins exceeds the level count.
#'        If you enable add_missing_NA, the returned length will be nbin+1.
#' @param plot A logical specifying whether to plot partial dependence table.
#' @param plot_stddev A logical specifying whether to add std err to partial dependence plot.
#' @param weight_column A string denoting which column of data should be used as the weight column.
#' @param include_na A logical specifying whether missing value should be included in the Feature values.
#' @param user_splits A two-level nested list containing user defined split points for pdp plots for each column.
#' If there are two columns using user defined split points, there should be two lists in the nested list.
#' Inside each list, the first element is the column name followed by values defined by the user.
#' @param col_pairs_2dpdp A two-level nested list like this: col_pairs_2dpdp = list(c("col1_name", "col2_name"),
#'   c("col1_name","col3_name"), ...,) where a 2D partial plots will be generated for col1_name, col2_name pair, for
#'   col1_name, col3_name pair and whatever other pairs that are specified in the nested list.    
#' @param save_to Fully qualified prefix of the image files the resulting plots should be saved to, e.g. '/home/user/pdp'.
#'  Plots for each feature are saved separately in PNG format, each file receives a suffix equal to the corresponding feature name, e.g. `/home/user/pdp_AGE.png`.
#'  If the files already exists, they will be overridden. Files are only saves if plot = TRUE (default).
#' @return Plot and list of calculated mean response tables for each feature requested.
#' @param row_index Row for which partial dependence will be calculated instead of the whole input frame.
#' @param targets Target classes for multinomial model.    
#' @param ... Mainly used for backwards compatibility, to allow deprecated parameters.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' prostate[, "CAPSULE"] <- as.factor(prostate[, "CAPSULE"] )
#' prostate[, "RACE"] <- as.factor(prostate[, "RACE"] )
#' prostate_gbm <- h2o.gbm(x = c("AGE", "RACE"),
#'                         y = "CAPSULE",
#'                         training_frame = prostate,
#'                         ntrees = 10,
#'                         max_depth = 5,
#'                         learn_rate = 0.1)
#' h2o.partialPlot(object = prostate_gbm, newdata = prostate, cols = c("AGE", "RACE"))
#'
#' iris_hex <- as.h2o(iris)
#' iris_gbm <- h2o.gbm(x = c(1:4), y = 5, training_frame = iris_hex)
#'
#' # one target class
#' h2o.partialPlot(object = iris_gbm, newdata = iris_hex, cols="Petal.Length", targets=c("setosa"))
#' # three target classes
#' h2o.partialPlot(object = iris_gbm, newdata = iris_hex, cols="Petal.Length", 
#'                  targets=c("setosa", "virginica", "versicolor"))
#' }
#' @export

h2o.partialPlot <- function(object, newdata, cols, destination_key, nbins=20, plot = TRUE, plot_stddev = TRUE,
                            weight_column=-1, include_na=FALSE, user_splits=NULL, col_pairs_2dpdp=NULL, save_to=NULL,
                            row_index=-1, targets=NULL, ...) {
  dots <- list(...)
  for (arg in names(dots)) {
      if (arg == 'data') {
          warning("argument 'data' is deprecated; please use 'newdata' instead.")
          if (missing(newdata))
              newdata <- dots$data else warning("ignoring 'data' as 'newdata' was also provided.")
      } else {
          stop(paste("unused argument", arg))
      }
  }
  if(!is(object, "H2OModel")) stop("object must be an H2Omodel")
  if( is(object, "H2OOrdinalModel")) stop("object must be a regression model or binary and multinomial classfier")
  if(!is(newdata, "H2OFrame")) stop("newdata must be H2OFrame")
  if(!is.numeric(nbins) | !(nbins > 0) ) stop("nbins must be a positive numeric")
  if(!is.logical(plot)) stop("plot must be a logical value")
  if(!is.logical(plot_stddev)) stop("plot must be a logical value")
  if(!is.logical(include_na)) stop("add_missing_NA must be a logical value")
  if((is(object, "H2OMultinomialModel"))){
    if(is.null(targets)) stop("targets parameter has to be set for multinomial classification")
    for(i in 1:length(targets)){
        if(!is.character(targets[i])) stop("targets parameter must be a list of string values")
    }
  }
  
  noPairs <- missing(col_pairs_2dpdp)
  noCols <- missing(cols)
  if(noCols && noPairs) cols <- object@parameters$x # set to default only if both are missing

  y <- object@parameters$y
  numCols <- 0
  numColPairs <- 0    
  if (!missing(cols)) { # check valid cols in cols for 1d pdp
    x <- cols
    args <- .verify_dataxy(newdata, x, y)
  }
  cpairs <- NULL
  if (!missing(col_pairs_2dpdp))   { # verify valid cols for 2d pdp
    for (onePair in col_pairs_2dpdp) {
      pargs <- .verify_dataxy(newdata, onePair, y)
      cpairs <-
        c(cpairs, paste0("[", paste (pargs$x, collapse = ','), "]"))
    }
    numColPairs <- length(cpairs)
  }

  if (is.numeric(weight_column) && (weight_column != -1)) {
      stop("weight_column should be a column name of your data frame.")
  } else if (is.character(weight_column)) { # weight_column_index is column name
    if (!weight_column %in% h2o.names(newdata))
      stop("weight_column_index should be one of your columns in your data frame.")
    else
      weight_column <- match(weight_column, h2o.names(newdata))-1
  }
  
  if (!is.numeric(row_index)) {
    stop("row_index should be numeric.")
  }
  
  parms <- list()
  if (!missing(col_pairs_2dpdp)) {
    parms$col_pairs_2dpdp <- paste0("[", paste (cpairs, collapse = ','), "]")
  }
  if (!missing(cols)) {
    parms$cols <- paste0("[", paste (args$x, collapse = ','), "]")
    numCols <- length(cols)
  }
  if(is.null(targets)){
    num_1d_pp_data <- numCols
  } else {
    num_1d_pp_data <- numCols * length(targets)
  }
  noCols <- missing(cols)
  parms$model_id  <- attr(object, "model_id")
  parms$frame_id <- attr(newdata, "id")
  parms$nbins <- nbins
  parms$weight_column_index <- weight_column
  parms$add_missing_na <- include_na
  parms$row_index <- row_index

  if (is.null(user_splits) || length(user_splits) == 0) {
    parms$user_cols <- NULL
    parms$user_splits <- NULL
    parms$num_user_splits <- NULL
  } else {
    user_cols <- c()
    user_values <- c()
    user_num_splits <- c()
    column_names <- h2o.names(newdata)
    for (ind in c(1:length(user_splits))) {
      aList <- user_splits[[ind]]
      csname <- aList[1]
      if (csname %in% column_names) {
        if (h2o.isnumeric(newdata[csname]) || h2o.isfactor(newdata[csname]) || h2o.getTypes(newdata)[[which(names(newdata) == csname)]] == "time") {
          nVal <- length(aList)-1
          if (h2o.isfactor(newdata[csname])) {
            domains <- h2o.levels(newdata[csname]) # enum values
            tempVal <- aList[2:length(aList)]
            intVals <- c(1:length(tempVal))
            for (eleind in c(1:nVal)) {
              eleIndex <- which(domains == tempVal[eleind])
              if (eleIndex>0) {
                intVals[eleind] <- which(domains == tempVal[eleind]) - 1
              } else {
                stop("Illegal enum value encountered.  To include missing values in your feature values, set include_na to TRUE")
              }
            }
            user_values <- c(user_values, intVals)
          } else {
            vals <- as.numeric(unlist(strsplit(aList[2:length(aList)], ",")))
            user_values <- c(user_values, vals)
          }

          user_num_splits <- c(user_num_splits, nVal)
          user_cols <- c(user_cols, csname)
        } else {
          stop ("Partial dependency plots are generated for numerical and categorical columns only.")
        }
      } else {
        stop(
          "column names used in user_splits are not valid.  They should be chosen from the columns of your data set"
        )
      }
    }
    parms$user_cols <- paste0("[", paste(user_cols, collapse=','), "]")
    parms$user_splits <- paste0("[", paste(user_values, collapse=','), "]")
    parms$num_user_splits <- paste0("[", paste(user_num_splits, collapse=','), "]")
  }
  
  if(!is.null(targets)) {
    parms$targets <- paste0("[", paste (targets, collapse = ','), "]")
  }

  if(!missing(destination_key)) parms$destination_key = destination_key

  res <- .h2o.__remoteSend(method = "POST", h2oRestApiVersion = 3, page = "PartialDependence/", .params = parms)
  .h2o.__waitOnJob(res$key$name)
  url <- gsub("/3/", "", res$dest$URL)
  res <- .h2o.__remoteSend(url, method = "GET", h2oRestApiVersion = 3)

  ## Change feature names to the original supplied, the following is okay because order is preserved
      
  pps <- res$partial_dependence_data
  min_y <- min(pps[[1]][,2])
  max_y <- max(pps[[1]][,2])
  min_lower <- min_y
  max_upper <- max_y
  col_name_index <- 1
  for (i in 1:length(pps)) {
    pp <- pps[[i]]
    if (!all(is.na(pp))) {
      min_y <- min(min_y, min(pp[,2])) 
      max_y <- max(max_y, max(pp[,2]))
      min_lower <- min(min_lower, pp[,2] - pp[,3])
      max_upper <- max(max_upper, pp[,2] + pp[,3])
      if (i <= num_1d_pp_data) {
        if(is.null(targets)){
          col_name_index <- i
          title <- paste("Partial dependency plot for", cols[col_name_index]) 
        } else if(!is.null(targets)){
          if(length(cols) > 1 && i %% length(cols) == 0) {
            col_name_index = col_name_index + 1
          }
          if(length(targets) > 1) {
            title <- paste("Partial dependency plot for", cols[col_name_index], "and classes\n", paste(targets, collapse=", "))
          } else {
            title <- paste("Partial dependency plot for", cols[col_name_index], "and class", targets)
          }
        }
        names(pps[[i]]) <-
          c(cols[col_name_index],
            "mean_response",
            "stddev_response",
            "std_error_mean_response")
        attr(pps[[i]],"description") <- title
      } else {
        names(pps[[i]]) <-
          c(col_pairs_2dpdp[[i-num_1d_pp_data]][1],
            col_pairs_2dpdp[[i-num_1d_pp_data]][2],
            "mean_response",
            "stddev_response",
            "std_error_mean_response")
        attr(pps[[i]],"description") <- paste('2D partial dependence plot for', col_pairs_2dpdp[[i-num_1d_pp_data]][1], "and", col_pairs_2dpdp[[i-num_1d_pp_data]][1])    
      }
    }
  }
  col_types <- unlist(h2o.getTypes(newdata))
  col_names <- names(newdata)
    
  pp.plot.1d <- function(pp) {
    if(!all(is.na(pp))) {
      x <- pp[,1]
      y <- pp[,2]
      stddev <- pp[,3] 
      type <- col_types[which(col_names == names(pp)[1])]
      if(type == "enum") {
        line_type <- "p"
        lty <- NULL
        pch <- 19
        pp[, 1] <- factor(pp[,1], levels=pp[,1])
      } else {
        line_type <- "l"
        lty <- 1
        pch <- NULL
      }
      ## Plot one standard deviation above and below the mean
      if(plot_stddev) {
        ## Added upper and lower std dev confidence bound
        upper <- y + stddev
        lower <- y - stddev
        plot(pp[,1:2], type = line_type, pch=pch, medpch=pch, medcol="red", medlty=0, staplelty=0, boxlty=0, col="red", main = attr(pp,"description"), ylim  = c(min(lower), max(upper)))
        pp.plot.1d.plotNA(pp, type, "red")
        polygon(pp.plot.1d.proccessDataForPolygon(c(pp[,1], rev(pp[,1])), c(lower, rev(upper))) , col = adjustcolor("red", alpha.f = 0.1), border = F)
        if(type == "enum"){
          x <- c(1:length(x))
          arrows(x, lower, x, upper, code=3, angle=90, length=0.1, col="red")
        }
      } else {
        plot(pp[,1:2], type = line_type, pch=pch, medpch=pch, medcol="red", medlty=0, staplelty=0, boxlty=0, col="red", main = attr(pp,"description"))
        pp.plot.1d.plotNA(pp, type, "red")
      }
    } else {
      print("Partial Dependence not calculated--make sure nbins is as high as the level count")
    }
  }
        
  pp.plot.1d.plotNA <- function(pp, type, color) {
    ## Plot NA value if numerical
    NAsIds <- which(is.na(pp[,1:1]))
    if (type != "enum" && include_na && length(NAsIds) != 0) {
        points(pp[,1:1],array(pp[NAsIds, 2:2], dim = c(length(pp[,1:1]), 1)), col=color, type="l", lty=5)
        if (is.null(targets)) {
          legend("topright", legend="NAN", col=color, lty=5, bty="n", ncol=length(pps))
        }
        return(TRUE)
    } else {
        return(FALSE)
    }
  }     
         
  pp.plot.1d.plotLegend.multinomial <- function(pp, targets, colors, lty, pch, has_NA) {
    if (include_na && length(which(is.na(pp[,1:1]))) != 0) {
      legendTargets <- c()
      legendColors <- c()
      legendLtys <- c()
      legendPchs <- c()
      for ( i in 1: length(targets)) {
        # target label
        legendTargets <- append(legendTargets, targets[i])
        legendColors <- append(legendColors, colors[i])
        legendLtys <- append(legendLtys, lty)
        legendPchs <- append(legendPchs, pch)
        # target NAN line label
        if (has_NA[i]) {
          legendTargets <- append(legendTargets, paste(targets[i], " NAN"))
          legendColors <- append(legendColors, colors[i])
          legendLtys <- append(legendLtys, 5)
        } 
        legendPchs <- append(legendPchs, NULL)
      }
      legend("topright", legend=legendTargets, col=legendColors, lty=legendLtys, pch=legendPchs, bty="n", ncol=length(pps))
    }  else {
      legend("topright",legend=targets, col=colors, lty=lty, pch=pch, bty="n", ncol=length(pps))
    }
  }
    
  pp.plot.1d.proccessDataForPolygon <- function(X, Y) {
    ## polygon can't handle NAs
    NAsIds <- which(is.na(X))
    if (length(NAsIds) != 0) {
      X <- X[-NAsIds]
      Y <- Y[-NAsIds]
    }
    return(cbind(X, Y))
  }        

  pp.plot.1d.multinomial <- function(pps) {
    colors <- rainbow(length(pps))
    has_NA <- c()
    for(i in 1:length(pps)) {
      pp <- pps[[i]]
      if(!all(is.na(pp))) {
        x <- pp[,1]
        y <- pp[,2]
        stddev <- pp[,3]
        color <- colors[i]
        title <- attr(pp,"description")
        type <- col_types[which(col_names == names(pp)[1])]
        if(type == "enum"){
           line_type <- "p"
           lty <- NULL
           pch <- 19
           pp[, 1] <- factor(x, labels=x)
        } else {
          line_type <- "l"
          lty <- 1
          pch <- NULL
        }
        if(plot_stddev) {
          upper <- y + stddev
          lower <- y - stddev
          if(i == 1){
            plot(pp[,1:2], type = line_type, pch=pch, medpch=pch, medcol=color, medlty=0, staplelty=0, boxlty=0, main = title, col = color, ylim  = c(min_lower, max_upper + 0.1 * abs(max_upper)))
          } else {
            points(pp[,1:2], type = line_type, pch=pch, medpch=pch, medcol=color, medlty=0, staplelty=0, boxlty=0, col = color)
          }
          has_NA <- append(has_NA, pp.plot.1d.plotNA(pp, type, color))
          polygon(pp.plot.1d.proccessDataForPolygon(c(x, rev(x)), c(lower, rev(upper))), col = adjustcolor(color, alpha.f = 0.1), border = F)   
          if(type == "enum"){
            x <- c(1:length(x))
            arrows(x, lower, x, upper, code=3, angle=90, length=0.1, col=color)
          }
        } else {
          if(i == 1) {
            plot(pp[,1:2], type = line_type, pch=pch, medpch=pch, medcol=color, medlty=0, staplelty=0, boxlty=0, main = title, col = color, ylim  = c(min_y, max_y + 0.05 * abs(max_y)))
          } else {
            points(pp[,1:2], type = line_type, pch=pch, medpch=pch, medcol=color, medlty=0, staplelty=0, boxlty=0, col = color) 
          }
          has_NA <- append(has_NA, pp.plot.1d.plotNA(pp, type, color))
        }
      } else {
        print("Partial Dependence not calculated--make sure nbins is as high as the level count")
      }
    }
    pp.plot.1d.plotLegend.multinomial(pp, targets, colors, lty, pch, has_NA)
  }      
        
  pp.plot.2d <- function(pp, nBins=nbins, user_cols=NULL, user_num_splits=NULL) {
    xtickMarks <- NULL
    ytickMarks <- NULL
    if (!all(is.na(pp))) {
      if (col_types[which(col_names == names(pp)[1])] == "enum") {
        x <- replaceEnumLevel(pp[,1], unique(pp[,1]))
        xtickMarks <- unique(pp[,1])
      } else {
        x <- pp[,1]
      }
      if (col_types[which(col_names == names(pp)[2])] == "enum") {
        y <- replaceEnumLevel(pp[,2], unique(pp[,2]))
        ytickMarks <- unique(pp[,2])
      } else {
        y <- pp[,2]
      }
      allMetric <- reShape(x, y, pp[, 3], names(pp)[1], names(pp)[2], nBins, user_cols, user_num_splits)
      XX <- allMetric[[1]]
      YY <- allMetric[[2]]
      ZZ <- allMetric[[3]]
      tTitle <- ""
      if (!is.null(xtickMarks)) {
        xc <- c(1:length(xtickMarks))
        tTitle <- paste0("X axis tick marks: ", paste(xc, xtickMarks, sep=":", collapse=", "))
      }
      if (!is.null(ytickMarks)) {
        yc <- c(1:length(ytickMarks))
        temp <- paste0("Y axis tick marks: ", paste(yc, ytickMarks, sep=":", collapse=", "))
        tTitle <- paste0(tTitle, temp)
      }
      ## Plot one standard deviation above and below the mean
      if (plot_stddev) {
        ## Added upper and lower std dev confidence bound
        upper <- pp[, 3] + pp[, 4]
        lower <- pp[, 3] - pp[, 4]
        Zupper <- matrix(upper, ncol=dim(XX)[2], byrow=F)
        Zlower <- matrix(lower, ncol=dim(XX)[2], byrow=F)
        rgl::open3d()
        plot3Drgl::persp3Drgl(XX, YY, ZZ, theta=30, phi=15, axes=TRUE,scale=2, box=TRUE, nticks=5,
                ticktype="detailed", xlab=names(pp)[1], ylab=names(pp)[2], zlab="2D partial plots",
                main=tTitle, border='black', alpha=0.5)
        plot3Drgl::persp3Drgl(XX, YY, Zupper, alpha=0.2, lwd=2, add=TRUE, border='yellow')
        plot3Drgl::persp3Drgl(XX, YY, Zlower, alpha=0.2, lwd=2, add=TRUE, border='green')
        rgl::grid3d(c("x", "y", "z"))
      } else {
        rgl::persp3d(XX, YY, ZZ, theta=30, phi=50, axes=TRUE,scale=2, box=TRUE, nticks=5,
                ticktype="detailed", xlab=names(pp)[1], ylab=names(pp)[2], zlab="2D partial plots",
                main=tTitle, border='black', alpha=0.5)
        rgl::grid3d(c("x", "y", "z"))
      }
    } else {
      print("2D Partial Dependence not calculated--make sure nbins is as high as the level count")
    }
  }
  
  pp.plot.save.1d <- function(pp) {
    # If user accidentally provides one of the most common suffixes in R, it is removed.
    save_to <- gsub(replacement = "",pattern = "(\\.png)|(\\.jpg)|(\\.pdf)", x = save_to)
    destination_file <- paste0(save_to,"_",names(pp)[1],'.png')
    png(destination_file)
    pp.plot.1d(pp)
    dev.off()
  }
      
  pp.plot.save.1d.multinomial <- function(pps) {
    # If user accidentally provides one of the most common suffixes in R, it is removed.
    save_to <- gsub(replacement = "",pattern = "(\\.png)|(\\.jpg)|(\\.pdf)", x = save_to)
    destination_file <- paste0(save_to,"_",names(pps[[1]])[1],'.png')
    png(destination_file)
    pp.plot.1d.multinomial(pps)
    dev.off()
}

  pp.plot.save.2d <- function(pp, nBins=nbins, user_cols=NULL, user_num_splits=NULL) {
    # If user accidentally provides one of the most common suffixes in R, it is removed.
    save_to <- gsub(replacement = "", pattern = "(\\.png)|(\\.jpg)|(\\.pdf)", x = save_to)
    colnames <- paste0(names(pp)[1], "_", names(pp)[2])
    destination_file <- paste0(save_to,"_",colnames,'.png')
    pp.plot.2d(pp, nbins, user_cols, user_num_splits)
    rgl::snapshot3d(destination_file)
    dev.off()
  }

  # 1D PDP plot and save    
  if(plot && !noCols) {
    if(is.null(targets)){ # multonomial PDP
      lapply(pps[1:num_1d_pp_data], pp.plot.1d)
      if(!is.null(save_to)){
        lapply(pps[1:num_1d_pp_data], pp.plot.save.1d)
      }
    } else {
      from <- 1
      to <- length(targets)
      for(i in 1:numCols) {
        pp <- pps[from:to]
        pp.plot.1d.multinomial(pp)
        if(!is.null(save_to)){
          pp.plot.save.1d.multinomial(pp)
        }
        from <- from + to
        to <- to + length(targets)
      }
    }
  }
          
  # 2D PDP plot and save
  if (!noPairs && requireNamespace("plot3Drgl", quietly = TRUE) && requireNamespace("rgl", quietly = TRUE)) {
    if (plot && !is.null(save_to)) {
      # plot and save to file
      if (is.null(user_splits)) {
        sapply(
          pps[(num_1d_pp_data + 1):(num_1d_pp_data + numColPairs)],
          pp.plot.save.2d,
          nBins = nbins,
          user_cols = NULL,
          user_num_splits = NULL
        )
      } else {
        sapply(
          pps[(num_1d_pp_data + 1):(num_1d_pp_data + numColPairs)],
          pp.plot.save.2d,
          nBins = nbins,
          user_cols = user_cols,
          user_num_splits = user_num_splits
        )
      }
    } else {
      # only plot
      if (is.null(user_splits)) {
        sapply(
          pps[(numCols + 1):(numCols + numColPairs)],
          pp.plot.2d,
          nBins = nbins,
          user_cols = NULL,
          user_num_splits = NULL
        )
      } else {
        sapply(
          pps[(numCols + 1):(numCols + numColPairs)],
          pp.plot.2d,
          nBins = nbins,
          user_cols = user_cols,
          user_num_splits = user_num_splits
        )
      }
    }
  } else if (plot && !noPairs) {
    warning("Install packages plot3Drgl and rgl in order to generate 2D partial plots.")     
  }

  if(length(pps) == 1) {
    return(pps[[1]])
  } else {
    return(pps)
  }
}

replaceEnumLevel <- function(originalV, vlevels) {
  x <- rep(1, length(originalV))
  for (ind in c(1:length(originalV))) {
    x[ind] <- which(originalV[ind] == vlevels)
  }
  x
}

reShape<- function(x, y, z, xname, yname, nbin, user_cols, user_num_splits) {
  ybin <- nbin
  if(!is.null(user_cols)) {
    if (yname %in% user_cols) {
      ybin <- user_num_splits[which(yname==user_cols)]
    }
  }

  xbin <- floor(length(x)/ybin)
  X<-matrix(x, nrow=ybin, ncol=xbin,byrow=F)
  Y <- matrix(y, nrow=ybin, ncol=xbin, byrow=F)
  Z <- matrix(z, nrow=ybin, ncol=xbin, byrow=F)
  list(X,Y,Z)
}

#' Feature Generation via H2O Deep Learning
#'
#' Extract the non-linear feature from an H2O data set using an H2O deep learning
#' model.
#' @param object An \linkS4class{H2OModel} object that represents the deep
#' learning model to be used for feature extraction.
#' @param data An H2OFrame object.
#' @param layer Index (integer) of the hidden layer to extract
#' @return Returns an H2OFrame object with as many features as the
#'         number of units in the hidden layer of the specified index.
#' @seealso \code{\link{h2o.deeplearning}} for making H2O Deep Learning models.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path = system.file("extdata", "prostate.csv", package = "h2o")
#' prostate = h2o.importFile(path = prostate_path)
#' prostate_dl = h2o.deeplearning(x = 3:9, y = 2, training_frame = prostate,
#'                                hidden = c(100, 200), epochs = 5)
#' prostate_deepfeatures_layer1 = h2o.deepfeatures(prostate_dl, prostate, layer = 1)
#' prostate_deepfeatures_layer2 = h2o.deepfeatures(prostate_dl, prostate, layer = 2)
#' head(prostate_deepfeatures_layer1)
#' head(prostate_deepfeatures_layer2)
#'
#' }
#' @export
h2o.deepfeatures <- function(object, data, layer) {
  url <- paste0('Predictions/models/', object@model_id, '/frames/', h2o.getId(data))
  if (is.null(layer)) layer <- 1
  if (is.numeric(layer)) {
    index = layer - 1
    res <- .h2o.__remoteSend(url, method = "POST", deep_features_hidden_layer=index, h2oRestApiVersion = 4)
  } else {
    res <- .h2o.__remoteSend(url, method = "POST", deep_features_hidden_layer_name=layer, h2oRestApiVersion = 4)
  }
  job_key <- res$key$name
  dest_key <- res$dest$name
  .h2o.__waitOnJob(job_key)
  h2o.getFrame(dest_key)
}

#'
#' The H2ONode class.
#'
#' @slot id An \code{integer} representing node's unique identifier. Generated by H2O.
#' @slot levels A \code{character} representing categorical levels on split from parent's node belonging into this node. NULL for root node or non-categorical splits.
#'
#' #' @aliases H2ONode
#'
setClass("H2ONode", slots = c(
  id = "integer"
))

#'
#' The H2OLeafNode class.
#'
#' This class represents a single leaf node in an \code{H2OTree}.
#'
#' #' @aliases H2OLeafNode
#'
setClass("H2OLeafNode", slots = c(
  prediction = "numeric"
),
contains = "H2ONode")

#'
#' The H2OSplitNode class.
#'
#' This class represents a single non-terminal node in an \code{H2OTree}.
#' @slot threshold A \code{numeric} split threshold, typically when the split column is numerical.
#' @slot left_child A \code{H2ONodeOrNULL} representing the left child node, if a node has one.
#' @slot right_child A \code{H2ONodeOrNULL} representing the right child node, if a node has one.
#' @slot split_feature A \code{character} representing the name of the column this node splits on.
#' @slot left_levels A \code{character} representing the levels of a categorical feature heading to the left child of this node. NA for non-categorical split.
#' @slot right_levels A \code{character} representing the levels of a categorical feature heading to the right child of this node. NA for non-categorical split.
#' @slot na_direction A \code{character} representing the direction of NA values. LEFT means NA values go to the left child node, RIGH means NA values go to the right child node.
#' @aliases H2OSplitNode
#' @export
setClass(
  "H2OSplitNode",
  slots = c(
    threshold = "numeric",
    left_child = "H2ONode",
    right_child = "H2ONode",
    split_feature = "character",
    left_levels = "character",
    right_levels = "character",
    na_direction = "character"
  ),
  contains = "H2ONode"
)

#' @rdname H2ONode-class
#' @param object an \code{H2ONode} object.
#' @export
setMethod('show', 'H2ONode',
          function(object){
            print.H2ONode(object)
          })

#' @method print H2ONode
#' @export
print.H2ONode <- function(x, ...){
  cat("Node ID", x@id, "\n\n")
  if (inherits(x, "H2OLeafNode")){
    cat("Terminal node. Prediction is", x@prediction)
    return()
  }


  if(!is.null(x@left_child)) cat("Left child node ID =", x@left_child@id, "\n") else cat("There is no left child \n")
  if(!is.null(x@right_child)) cat("Right child node ID =", x@right_child@id,"\n") else cat("There is no right child \n")
  cat("\n")
  cat("Splits on column", x@split_feature, "\n")

  if(is.na(x@threshold)){
    if(!is.null(x@left_child)) cat("  - Categorical levels going to the left node:", x@left_levels, "\n")
    if(!is.null(x@right_child)) cat("  - Categorical levels to the right node:", x@right_levels, "\n")
  } else {
    cat("Split threshold <", x@threshold,"to the left node, >=",x@threshold ,"to the right node\n")
  }
  cat("\n")
  if(!is.na(x@na_direction)) cat("NA values go to the", x@na_direction,"node")
}

#'
#' The H2OTree class.
#'
#' This class represents a model of a Tree built by one of H2O's algorithms (GBM, Random Forest).
#' @slot root_node A \code{H2ONode} representing the beginning of the tree behind the model. Allows further tree traversal.
#' @slot left_children An \code{integer} vector with left child nodes of tree's nodes
#' @slot right_children An \code{integer} vector with right child nodes of tree's nodes
#' @slot node_ids An \code{integer} representing identification number of a node. Node IDs are generated by H2O.
#' @slot descriptions A \code{character} vector with descriptions for each node to be found in the tree. Contains split threshold if the split is based on numerical column.
#'                    For cactegorical splits, it contains list of categorical levels for transition from the parent node.
#' @slot model_id A \code{character} with the name of the model this tree is related to.
#' @slot tree_number An \code{integer} representing the order in which the tree has been built in the model.
#' @slot tree_class A \code{character} representing name of tree's class. Number of tree classes equals to the number of levels in categorical response column.
#'                  As there is exactly one class per categorical level, name of tree's class equals to the corresponding categorical level of response column.
#'                  In case of regression and binomial, the name of the categorical level is ignored can be omitted, as there is exactly one tree built in both cases.
#' @slot thresholds A \code{numeric} split thresholds. Split thresholds are not only related to numerical splits, but might be present in case of categorical split as well.
#' @slot features A \code{character} with names of the feature/column used for the split.
#' @slot levels A \code{character} representing categorical levels on split from parent's node belonging into this node. NULL for root node or non-categorical splits.
#' @slot nas A \code{character} representing if NA values go to the left node or right node. May be NA if node is a leaf.
#' @slot predictions A \code{numeric} representing predictions for each node in the graph.
#' @slot tree_decision_path A \code{character}, plain language rules representation of a trained decision tree    
#' @slot decision_paths A \code{character} representing plain language rules that were used in a particular prediction.
#' @slot left_cat_split A \code{character} list of categorical levels leading to the left child node. Only present when split is categorical, otherwise none.
#' @slot right_cat_split A \code{character} list of categorical levels leading to the right child node. Only present when split is categorical, otherwise none.
#' @aliases H2OTree
#' @export
setClass(
  "H2OTree",
  slots = c(
    root_node = "H2ONode",
    left_children = "integer",
    right_children = "integer",
    node_ids = "integer",
    descriptions = "character",
    model_id = "character",
    tree_number = "integer",
    tree_class = "character",
    thresholds = "numeric",
    features = "character",
    levels = "list",
    nas = "character",
    predictions = "numeric",
    tree_decision_path = "character",
    decision_paths = "character",
    left_cat_split = "list",
    right_cat_split = "list"
  )
)

#' @rdname H2OTree-class
#' @param object an \code{H2OTree} object.
#' @export
setMethod('show', 'H2OTree',
          function(object){
            print.H2OTree(object)
          })

#' @method print H2OTree
#' @export
print.H2OTree <- function(x, ...){
  cat(paste0("Tree related to model '", x@model_id,"'. Tree number is"), paste0(x@tree_number,", tree class is '",x@tree_class, "'\n"))
  cat("The tree has", length(x), "nodes")
}

#'
#' Overrides the behavior of length() function on H2OTree class. Returns number of nodes in an \code{H2OTree}
#' @param x An \code{H2OTree} to count nodes for.
#'
setMethod("length", signature(x = "H2OTree"), function(x) {
  length(x@left_children)
})


.h2o.walk_tree <- function(node, tree){
  if(node == -1) {return(NULL)}
  child_node_index <- node + 1
  left <- tree@left_children[child_node_index]
  right <- tree@right_children[child_node_index]

  node_levels <- if(is.null(tree@levels[[node + 1]])) NA_character_ else tree@levels[[node + 1]]

  left_child = .h2o.walk_tree(left, tree)
  right_child = .h2o.walk_tree(right, tree)

  node <- NULL
  if(is.null(left_child) && is.null(right_child)){
    node <- new("H2OLeafNode",
        id = tree@node_ids[child_node_index],
        prediction = tree@predictions[child_node_index]
        )
  } else {
      left_node_levels <- if(is.null(tree@levels[[left + 1]])) NA_character_ else tree@levels[[left + 1]]
      right_node_levels <- if(is.null(tree@levels[[right + 1]])) NA_character_ else tree@levels[[right + 1]]
      node <- new ("H2OSplitNode",
       id = tree@node_ids[child_node_index],
       left_child = left_child,
       right_child = right_child,
       threshold = tree@thresholds[child_node_index],
       split_feature = tree@features[child_node_index],
       na_direction = tree@nas[child_node_index],
       left_levels = left_node_levels,
       right_levels = right_node_levels)
  }

  node
}

#' Fetchces a single tree of a H2O model. This function is intended to be used on Gradient Boosting Machine models or Distributed Random Forest models.
#'
#'
#' @param model Model with trees
#' @param tree_number Number of the tree in the model to fetch, starting with 1
#' @param tree_class Name of the class of the tree (if applicable). This value is ignored for regression and binomial response column, as there is only one tree built.
#'                   As there is exactly one class per categorical level, name of tree's class equals to the corresponding categorical level of response column.
#' @param plain_language_rules (Optional) Whether to generate plain language rules. AUTO by default, meaning FALSE for big trees and TRUE for small trees.
#' @return Returns an H2OTree object with detailed information about a tree.
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' f <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/iris/iris_train.csv"
#' iris <- h2o.importFile(f)
#' gbm_model <- h2o.gbm(y = "species", training_frame = iris)
#' tree <- h2o.getModelTree(gbm_model, 1, "Iris-setosa")
#' }
#' @export
h2o.getModelTree <- function(model, tree_number, tree_class = NA, plain_language_rules="AUTO") {
  url <- "Tree"
  tree_class_request = tree_class;
  if(is.na(tree_class)){
    tree_class_request <- "";
  }
  res <- .h2o.__remoteSend(
      url,
      method = "GET",
      h2oRestApiVersion = 3,
      model = model@model_id,
      tree_number = tree_number - 1,
      tree_class = tree_class_request,
      plain_language_rules = plain_language_rules
    )

  res$thresholds[is.nan(res$thresholds)] <- NA
  
  if(length(res$left_children) < 1) stop("Tree does not contain any nodes.")

  if(res$left_children[1] == -1){ # If the root node has no children
    res$nas <- c("NA")
    res$levels <- list(NULL)
    res$thresholds <- c(as.double(NA))
  }
  
  # Protection against NA only arrays being evaluated as logical
  if(is.logical(res$features)){
    res$features <- as.character(res$features)
  }
  
  if(is.logical(res$nas)){
    res$nas <- as.character(res$nas)
  }
  
  if(is.logical(res$thresholds)){
    res$thresholds <- as.numeric(res$thresholds)
  }
  
  if(is.logical(res$predictions)){
    res$predictions <- as.numeric(res$predictions)
  }
  
  if(is.logical(res$predictions)){
    res$predictions <- as.numeric(res$predictions)
  }
  
  # Start of the tree-building process
  tree <- new(
    "H2OTree",
    left_children = res$left_children,
    right_children = res$right_children,
    descriptions = res$descriptions,
    model_id = model@model_id,
    tree_number = as.integer(res$tree_number + 1),
    thresholds = res$thresholds,
    features = res$features,
    nas = res$nas,
    predictions = res$predictions,
    tree_decision_path = ifelse(is.null(res$tree_decision_path), "Plain language rules generation is turned off.", res$tree_decision_path),
    decision_paths = ifelse(is.na(res$decision_paths[1]), "Plain language rules generation is turned off.", res$decision_paths)
  )

  node_index <- 0
  left_ordered <- c()
  right_ordered <- c()
  node_ids <- c(res$root_node_id)

  for(i in 1:length(tree@left_children)){
    if(tree@left_children[i] != -1){
      node_index <- node_index + 1
      left_ordered[i] <- node_index
      node_ids[node_index + 1] <- tree@left_children[i]
    } else {
      left_ordered[i] <- -1
    }

    if(tree@right_children[i] != -1){
      node_index <- node_index + 1
      right_ordered[i] <- node_index
      node_ids[node_index + 1] <- tree@right_children[i]
    } else {
      right_ordered[i] <- -1
    }
  }

  tree@node_ids <- node_ids
  tree@left_children <- as.integer(left_ordered)
  tree@right_children <- as.integer(right_ordered)

  if(!is.null(res$tree_class)){
    tree@tree_class <- res$tree_class
  }

  if(is.logical(res$levels)){ # Vector of NAs is recognized as logical type in R
    tree@levels <- rep(list(NULL), length(res$levels))
  } else {
    tree@levels <- res$levels
  }

  for (i in 1:length(tree@levels)){
    if(!is.null(tree@levels[[i]])){
    tree@levels[[i]] <- tree@levels[[i]] + 1
    }
  }

  # Convert numerical categorical levels to characters
  pointer <-as.integer(1);
  for(i in 1:length(tree@left_children)){

    right <- tree@right_children[i];
    left <- tree@left_children[i]
    split_column_cat_index <- match(tree@features[i], model@model$names) # Indexof split column on children's parent node
    if(is.na(split_column_cat_index)){ # If the split is not categorical, just increment & continue
      if(right != -1) pointer <- pointer + 1;
      if(left != -1) pointer <- pointer + 1;
      next
    }
    split_column_domain <- model@model$domains[[split_column_cat_index]]

    # Left child node's levels converted to characters
    left_char_categoricals <- c()
    if(left != -1)  {
      pointer <- pointer + 1;

      if(!is.null(tree@levels[[pointer]])){
        for(level_index in 1:length(tree@levels[[pointer]])){
          left_char_categoricals[level_index] <- split_column_domain[tree@levels[[pointer]][level_index]]
        }
        tree@levels[[pointer]] <- left_char_categoricals;
      }
    }


    # Right child node's levels converted to characters, if there is any
    right_char_categoricals <- c()
    if(right != -1)  {
      pointer <- pointer + 1;
      if(!is.null(tree@levels[[pointer]])){
        for(level_index in 1:length(tree@levels[[pointer]])){
          right_char_categoricals[level_index] <- split_column_domain[tree@levels[[pointer]][level_index]]
        }
        tree@levels[[pointer]] <- right_char_categoricals
      }
    }
  }
  
  for (i in 1: length(tree@left_children)){
    left_idx = tree@left_children[i]
    right_idx = tree@right_children[i]
    
    if(left_idx != -1){
      tree@left_cat_split[i] <- tree@levels[left_idx + 1]
    } else {
      tree@left_cat_split[i] <- NULL
    }
    
    if(right_idx != -1){
      tree@right_cat_split[i] <- tree@levels[right_idx + 1]
    } else {
      tree@right_cat_split[i] <- NULL
    }
  }
  
  tree@root_node <- .h2o.walk_tree(0, tree)
  tree
}

#' @export
print.h2o.stackedEnsemble.summary <- function(x, ...) cat(x, sep = "\n")

#' Get the seed from H2OModel which was used during training.
#' If a user does not set the seed parameter before training, the seed is autogenerated.
#' It returns seed as the string if the value is bigger than the integer.
#' For example, an autogenerated seed is always long so that the seed in R is a string.
#'
#' @param object a fitted \linkS4class{H2OModel} object.
#' @return Returns seed to be used during training a model. Could be numeric or string.
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.uploadFile(path = prostate_path)
#' prostate$CAPSULE <- as.factor(prostate$CAPSULE)
#' prostate_gbm <- h2o.gbm(3:9, "CAPSULE", prostate)
#' seed <- h2o.get_seed(prostate_gbm)
#' }
#' @export
get_seed.H2OModel <- function(object) {
    object@parameters$seed
}

#' @rdname get_seed.H2OModel
#' @export
h2o.get_seed <- get_seed.H2OModel


#' Imports a model under given path, creating a Generic model with it.
#'
#' Usage example:
#' generic_model <- h2o.genericModel(model_file_path = "/path/to/mojo.zip")
#' predictions <- h2o.predict(generic_model, dataset)
#'
#' @param mojo_file_path Filesystem path to the model imported
#' @param model_id Model ID, default is NULL
#' @return Returns H2O Generic Model based on given embedded model
#'
#' @examples
#' \dontrun{
#'
#' # Import default Iris dataset as H2O frame
#' data <- as.h2o(iris)
#'
#' # Train a very simple GBM model
#' features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
#' original_model <- h2o.gbm(x = features, y = "Species", training_frame = data)
#'
#' # Download the trained GBM model as MOJO (temporary directory used in this example)
#' mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
#' mojo_original_path <- paste0(tempdir(), "/", mojo_original_name)
#'
#' # Import the MOJO as Generic model
#' generic_model <- h2o.genericModel(mojo_original_path)
#'
#' # Perform scoring with the generic model
#' generic_model_predictions  <- h2o.predict(generic_model, data)
#' }
#' @export
h2o.genericModel <- function(mojo_file_path, model_id=NULL){
  h2o.generic(path = mojo_file_path)
}

#' Imports a MOJO under given path, creating a Generic model with it.
#'
#' Usage example:
#' mojo_model <- h2o.import_mojo(model_file_path = "/path/to/mojo.zip")
#' predictions <- h2o.predict(mojo_model, dataset)
#'
#' @param mojo_file_path Filesystem path to the model imported
#' @param model_id Model ID, default is NULL
#' @return Returns H2O Generic Model embedding given MOJO model
#'
#' @examples
#' \dontrun{
#'
#' # Import default Iris dataset as H2O frame
#' data <- as.h2o(iris)
#'
#' # Train a very simple GBM model
#' features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
#' original_model <- h2o.gbm(x = features, y = "Species", training_frame = data)
#'
#' # Download the trained GBM model as MOJO (temporary directory used in this example)
#' mojo_original_path <- h2o.save_mojo(original_model, path = tempdir())
#'
#' # Import the MOJO and obtain a Generic model
#' mojo_model <- h2o.import_mojo(mojo_original_path)
#'
#' # Perform scoring with the generic model
#' predictions  <- h2o.predict(mojo_model, data)
#' }
#' @export
h2o.import_mojo <- function(mojo_file_path, model_id=NULL){
  model <- h2o.generic(path = mojo_file_path, model_id)
  return(model)
}


#' Imports a MOJO from a local filesystem, creating a Generic model with it.
#'
#' Usage example:
#' mojo_model <- h2o.upload_mojo(model_file_path = "/path/to/local/mojo.zip")
#' predictions <- h2o.predict(mojo_model, dataset)
#'
#' @param mojo_local_file_path Filesystem path to the model imported
#' @param model_id Model ID, default is NULL
#' @return Returns H2O Generic Model embedding given MOJO model
#'
#' @examples
#' \dontrun{
#'
#' # Import default Iris dataset as H2O frame
#' data <- as.h2o(iris)
#'
#' # Train a very simple GBM model
#' features <- c("Sepal.Length", "Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width")
#' original_model <- h2o.gbm(x = features, y = "Species", training_frame = data)
#'
#' # Download the trained GBM model as MOJO (temporary directory used in this example)
#' mojo_original_name <- h2o.download_mojo(model = original_model, path = tempdir())
#' mojo_original_path <- paste0(tempdir(), "/", mojo_original_name)
#'
#' # Upload the MOJO from local filesystem and obtain a Generic model
#' mojo_model <- h2o.upload_mojo(mojo_original_path)
#'
#' # Perform scoring with the generic model
#' predictions  <- h2o.predict(mojo_model, data)
#' }
#' @export
h2o.upload_mojo <- function(mojo_local_file_path, model_id=NULL){
  model_file_key <- h2o.uploadFile(mojo_local_file_path, parse = FALSE)
  model <- h2o.generic(model_key = model_file_key, model_id = model_id)
  return(model)
}

#'
#' Reset model threshold and return old threshold value.
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param threshold A threshold value from 0 to 1 included.
#' @return Returns the previous threshold used in the model.
#'
#' @examples 
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' 
#' prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
#' prostate <- h2o.importFile(prostate_path)
#' prostate[, 2] <- as.factor(prostate[, 2])
#' prostate_glm <- h2o.glm(y = "CAPSULE", x = c("AGE", "RACE", "PSA", "DCAPS"), 
#'                         training_frame = prostate, family = "binomial", 
#'                         nfolds = 0, alpha = 0.5, lambda_search = FALSE)
#' old_threshold <- h2o.reset_threshold(prostate_glm, 0.9)
#' }
#' @export
h2o.reset_threshold <- function(object, threshold) {
  o <- object
  if( is(o, "H2OModel") ) {
    .newExpr("model.reset.threshold", list(o@model_id, threshold))[1,1]
  } else {
    warning( paste0("Threshold cannot be reset for class ", class(o)) )
    return(NULL)
  }
}

#' Calculates per-level mean of predicted value vs actual value for a given variable.
#'
#' In the basic setting, this function is equivalent to doing group-by on variable and calculating
#' mean on predicted and actual. In addition to that it also handles NAs in response and weights
#' automatically.
#'
#' @param object    A trained supervised H2O model.
#' @param newdata   Input frame (can be training/test/.. frame).
#' @param predicted Frame of predictions for the given input frame.
#' @param variable  Name of variable to inspect.
#' @return          H2OTable
#' @export
h2o.predicted_vs_actual_by_variable <- function(object,
                                                newdata,
                                                predicted,
                                                variable
) {
  if (missing(object)) stop("Parameter 'object' needs to be specified.")
  if (!is(object, "H2OModel")) stop("Parameter 'object' has to be an H2O model.")
  .validate.H2OFrame(newdata, required = TRUE)

  vi <- as.data.frame(.newExpr("predicted.vs.actual.by.var",
                               object@model_id,
                               newdata,
                               paste0("'", variable, "'"),
                               predicted
  ), check.names = FALSE)
  oldClass(vi) <- c("H2OTable", "data.frame")
  attr(vi, "header") <- "Predicted vs Actual by Variable"
  attr(vi, "description") <- ""
  attr(vi, "formats") <- c("%s", rep_len("%5f", ncol(vi) - 1))
  vi
}

#' Create a leaderboard from a list of models, grids and/or automls.
#'
#' @param object List of models, automls, or grids; or just single automl/grid object.
#' @param leaderboard_frame Frame used for generating the metrics (optional).
#' @param sort_metric Metric used for sorting the leaderboard.
#' @param extra_columns What extra columns should be calculated (might require leaderboard_frame). Use "ALL" for all available or list of extra columns.
#' @param scoring_data Metrics to be reported in the leaderboard ("xval", "train", or "valid"). Used if no leaderboard_frame is provided.
#' @return data.frame
#'
#' @examples
#' \dontrun{
#' library(h2o)
#' h2o.init()
#' iris_hf <- as.h2o(iris)
#' grid <- h2o.grid("gbm", x = c(1:4), y = 5, training_frame = iris_hf,
#'                  hyper_params = list(ntrees = c(1, 2, 3)))
#' h2o.make_leaderboard(grid, iris_hf)
#' }
#' @export
h2o.make_leaderboard <- function(object,
                                 leaderboard_frame,
                                 sort_metric = "AUTO",
                                 extra_columns = c(),
                                 scoring_data = c("AUTO", "train", "valid", "xval")
) {
  .get_models <- function(obj){
    if (is.list(obj)) {
      return(lapply(obj, .get_models))
    } else if (.is.H2OAutoML(obj)) {
      return(unlist(as.list(obj@leaderboard$model_id)))
    } else if (inherits(obj, "H2OGrid")) {
      return(unlist(obj@model_ids))
    } else if (is.character(obj)) {
      return(obj)
    } else if (inherits(obj, "H2OModel")) {
      return(h2o.keyof(obj))
    } else {
      stop("Unsupported object!")
    }
  }
  model_ids <- unlist(.get_models(object))
  extra_cols <- paste0(extra_columns, collapse = "\", \"")
  scoring_data <- match.arg(scoring_data)
  if (missing(leaderboard_frame) || is.null(leaderboard_frame)) {
    leaderboard_frame_key <- NULL
  } else {
    # make sure the frame has assigned a key in R, this is necessary when subsetting h2o frame but not evaluating the subset
    if (is.null(h2o.keyof(leaderboard_frame))) head(leaderboard_frame, n = 1)
    leaderboard_frame_key <- h2o.keyof(leaderboard_frame)
  }

  as.data.frame(.newExpr("makeLeaderboard",
                         model_ids,
                         paste0("\"", leaderboard_frame_key, "\""),
                         paste0("\"", sort_metric, "\""),
                         paste0("[\"", extra_cols, "\"]"),
                         paste0("\"", scoring_data, "\"")
  ), check.names = FALSE)
}
