#'
#' H2O Model Related Functions
#'
#' @importFrom graphics strwidth par legend polygon


# ------------------------------- Helper Functions --------------------------- #
# Used to verify data, x, y and turn into the appropriate things
.verify_dataxy <- function(data, x, y, autoencoder = FALSE) {
  if(!is.character(x) && !is.numeric(x))
    stop('`x` must be column names or indices')
  if( !autoencoder )
    if(!is.character(y) && !is.numeric(y))
      stop('`y` must be a column name or index')

  cc <- colnames(chk.H2OFrame(data))

  if(is.character(x)) {
    if(!all(x %in% cc))
      stop("Invalid column names: ", paste(x[!(x %in% cc)], collapse=','))
    x_i <- match(x, cc)
  } else {
    if(any( x < 1L | x > attr(x,'ncol')))
      stop('out of range explanatory variable ', paste(x[x < 1L | x > length(cc)], collapse=','))
    x_i <- x
    x <- cc[x_i]
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

    if(!autoencoder && (y %in% x)) {
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
  job <- .h2o.startModelJob(algo, params, h2oRestApiVersion)
  h2o.getFutureModel(job, verbose = verbose)
}

.h2o.startModelJob <- function(algo, params, h2oRestApiVersion) {
  .key.validate(params$key)
  #---------- Force evaluate temporary ASTs ----------#
  ALL_PARAMS <- .h2o.__remoteSend(method = "GET", h2oRestApiVersion = h2oRestApiVersion, .h2o.__MODEL_BUILDERS(algo))$model_builders[[algo]]$parameters

  # R treats integer as not numeric: FIXME move into checkAndUnifyModelParameters
  params <- lapply(params, function(x) { if(is.integer(x)) x <- as.numeric(x); x })
  #---------- Check user parameter types ----------#
  param_values <- .h2o.checkAndUnifyModelParameters(algo = algo, allParams = ALL_PARAMS, params = params)
  #---------- Validate parameters ----------#
  #.h2o.validateModelParameters(algo, param_values, h2oRestApiVersion)
  #---------- Build! ----------#
  res <- .h2o.__remoteSend(method = "POST", .h2o.__MODEL_BUILDERS(algo), .params = param_values, h2oRestApiVersion = h2oRestApiVersion)
  if(length(res$messages) != 0L){
    warn <- lapply(res$messages, function(y) {
      if(class(y) == "list" && y$message_type == "WARN" )
        paste0(y$message, ".\n")
      else ""
    })
    if(any(nzchar(warn))) warning(warn)
  }
  job_key  <- res$job$key$name
  dest_key <- res$job$dest$name
  new("H2OModelFuture",job_key=job_key, model_id=dest_key)
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
  h2o.getFutureModel(.h2o.startModelJob(algo, params, h2oRestApiVersion))
}

#' Get future model
#'
#' @rdname h2o.getFutureModel
#' @param object H2OModel
#' @param verbose Print model progress to console. Default is FALSE
#' @export
h2o.getFutureModel <- function(object,verbose=FALSE) {
  .h2o.__waitOnJob(object@job_key,verboseModelScoringHistory=verbose)
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
  # First verify all parameters
  error <- lapply(allParams, function(i) {
    e <- ""
    name <- i$name
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

  if(any(nzchar(error)))
    stop(error)

  #---------- Create parameter list to pass ----------#
  param_values <- lapply(params, function(i) {
    if(is.H2OFrame(i))  h2o.getId(i)
    else             i
  })

  param_values
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
    if (!inherits(paramValue, type)) {
      e <- paste0("\"", name , "\" must be of type ", type, ", but got ", class(paramValue), ".\n")
    } else if ((length(paramDef$values) > 1L) && !(paramValue %in% paramDef$values)) {
      e <- paste0("\"", name,"\" must be in")
      for (fact in paramDef$values)
        e <- paste0(e, " \"", fact, "\",")
      e <- paste(e, "but got", paramValue)
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
      if (type == "character")
        paramValue <- .collapse.char(paramValue)
      else if (paramDef$type == "StringPair[]")
        paramValue <- .collapse(sapply(paramValue, .collapse.tuple))
      else
        paramValue <- .collapse(paramValue)
    }
  }
  if( is.H2OFrame(paramValue) )
    paramValue <- h2o.getId(paramValue)
  paramValue
}

.collapse.tuple <- function(x) {
  names <- names(x)
  if (is.null(names))
    names <- letters[1:length(x)]
  r <- c()
  for (i in 1:length(x)) {
    s <- paste0(names[i], ": \"", x[i], "\"")
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
#' @export
predict.H2OModel <- function(object, newdata, ...) {
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

#' @rdname predict.H2OModel
#' @export
h2o.predict <- function(object, newdata, ...){
  if(class(object) == "H2OAutoML"){
    return(predict.H2OAutoML(object, newdata, ...))
  }else{
    return(predict.H2OModel(object, newdata, ...))
  }
}
#' Predict the Leaf Node Assignment on an H2O Model
#'
#' Obtains leaf node assignment from fitted H2O model objects.
#'
#' For every row in the test set, return a set of factors that identify the leaf placements
#' of the row in all the trees in the model.
#' The order of the rows in the results is the same as the order in which the
#' data was loaded
#'
#' @param object a fitted \linkS4class{H2OModel} object for which prediction is
#'        desired
#' @param newdata An H2OFrame object in which to look for
#'        variables with which to predict.
#' @param ... additional arguments to pass on.
#' @return Returns an H2OFrame object with categorical leaf assignment identifiers for
#'         each tree in the model.
#' @seealso \code{\link{h2o.gbm}} and  \code{\link{h2o.randomForest}} for model
#'          generation in h2o.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
#' prostate.gbm <- h2o.gbm(3:9, "CAPSULE", prostate.hex)
#' h2o.predict(prostate.gbm, prostate.hex)
#' h2o.predict_leaf_node_assignment(prostate.gbm, prostate.hex)
#' }
#' @export
predict_leaf_node_assignment.H2OModel <- function(object, newdata, ...) {
  if (missing(newdata)) {
    stop("predictions with a missing `newdata` argument is not implemented yet")
  }

  url <- paste0('Predictions/models/', object@model_id, '/frames/',  h2o.getId(newdata))
  res <- .h2o.__remoteSend(url, method = "POST", leaf_node_assignment=TRUE)
  res <- res$predictions_frame
  h2o.getFrame(res$name)
}

#' @rdname predict_leaf_node_assignment.H2OModel
#' @export
h2o.predict_leaf_node_assignment <- predict_leaf_node_assignment.H2OModel

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

#' Model Performance Metrics in H2O
#'
#' Given a trained h2o model, compute its performance on the given
#' dataset
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
#' For more information visit: \url{https://0xdata.atlassian.net/browse/TN-9}
#' @param valid A logical value indicating whether to return the validation metrics (constructed during training).
#' @param xval A logical value indicating whether to return the cross-validation metrics (constructed during training). 
#' @param data (DEPRECATED) An H2OFrame. This argument is now called `newdata`.
#' @return Returns an object of the \linkS4class{H2OModelMetrics} subclass.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
#' prostate.gbm <- h2o.gbm(3:9, "CAPSULE", prostate.hex)
#' h2o.performance(model = prostate.gbm, newdata=prostate.hex)
#' 
#' ## If model uses balance_classes
#' ## the results from train = TRUE will not match the results from newdata = prostate.hex
#' prostate.gbm.balanced <- h2o.gbm(3:9, "CAPSULE", prostate.hex, balance_classes = TRUE)
#' h2o.performance(model = prostate.gbm.balanced, newdata = prostate.hex)
#' h2o.performance(model = prostate.gbm.balanced, train = TRUE)
#' }
#' @export
h2o.performance <- function(model, newdata=NULL, train=FALSE, valid=FALSE, xval=FALSE, data=NULL) {

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
  
  missingNewdata <- missing(newdata) || is.null(newdata)
  
  if( !missingNewdata ) {
    newdata.id <- h2o.getId(newdata)
    parms <- list()
    parms[["model"]] <- model@model_id
    parms[["frame"]] <- newdata.id
    res <- .h2o.__remoteSend(method = "POST", .h2o.__MODEL_METRICS(model@model_id,newdata.id), .params = parms)

    ####
    # FIXME need to do the client-side filtering...  PUBDEV-874:   https://0xdata.atlassian.net/browse/PUBDEV-874
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
#' @return Returns an object of the \linkS4class{H2OModelMetrics} subclass.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
#' prostate.gbm <- h2o.gbm(3:9, "CAPSULE", prostate.hex)
#' pred <- h2o.predict(prostate.gbm, prostate.hex)[,3] ## class-1 probability
#' h2o.make_metrics(pred,prostate.hex$CAPSULE)
#' }
#' @export
h2o.make_metrics <- function(predicted, actuals, domain=NULL, distribution=NULL) {
  params <- list()
  pred <- h2o.getId(predicted)
  act <- h2o.getId(actuals)
  params[["predictions_frame"]] <- pred
  params[["actuals_frame"]] <- act
  params[["domain"]] <- domain
  params[["distribution"]] <- distribution

  if (is.null(domain) && !is.null(h2o.levels(actuals)))
    domain = h2o.levels(actuals)

  ## pythonify the domain
  if (!is.null(domain)) {
    out <- paste0('["',domain[1],'"')
    for (d in 2:length(domain)) {
      out <- paste0(out,',"',domain[d],'"')
    }
    out <- paste0(out, "]")
    params[["domain"]] <- out
  }
  url <- paste0("ModelMetrics/predictions_frame/",pred,"/actuals_frame/",act)
  res <- .h2o.__remoteSend(method = "POST", url, .params = params)
  model_metrics <- res$model_metrics
  metrics <- model_metrics[!(names(model_metrics) %in% c("__meta", "names", "domains", "model_category"))]
  name <- "H2ORegressionMetrics"
  if (!is.null(metrics$AUC)) name <- "H2OBinomialMetrics"
  else if (distribution == "ordinal") name <- "H2OOrdinalMetrics"
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
#' @param object An \linkS4class{H2OBinomialMetrics} object.
#' @param train Retrieve the training AUC
#' @param valid Retrieve the validation AUC
#' @param xval Retrieve the cross-validation AUC
#' @seealso \code{\link{h2o.giniCoef}} for the Gini coefficient,
#'          \code{\link{h2o.mse}} for MSE, and \code{\link{h2o.metric}} for the
#'          various threshold metrics. See \code{\link{h2o.performance}} for
#'          creating H2OModelMetrics objects.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' hex <- h2o.uploadFile(prosPath)
#'
#' hex[,2] <- as.factor(hex[,2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = hex, distribution = "bernoulli")
#' perf <- h2o.performance(model, hex)
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
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' hex <- h2o.uploadFile(prosPath)
#'
#' hex[,2] <- as.factor(hex[,2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = hex, distribution = "bernoulli")
#' perf <- h2o.performance(model, hex)
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
#' \donttest{
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prosPath)
#' p.sid <- h2o.runif(prostate.hex)
#' prostate.train <- h2o.assign(prostate.hex[p.sid > .2,], "prostate.train")
#' prostate.glm <- h2o.glm(x=3:7, y=2, training_frame=prostate.train)
#' aic.basic <- h2o.aic(prostate.glm)
#' print(aic.basic)
#' }
#' @export
h2o.aic <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  if( is(object, "H2OModelMetrics") ) return( object@metrics$AIC )
  if( is(object, "H2OModel") ) {
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
#' Retrieve the R2 value
#'
#' Retrieves the R2 value from an H2O model.
#' Will return R^2 for GLM Models and will return NaN otherwise.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training R2 value is returned. If more
#' than one parameter is set to TRUE, then a named vector of R2s are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OModel} object.
#' @param train Retrieve the training R2
#' @param valid  Retrieve the validation set R2 if a validation set was passed in during model build time.
#' @param xval Retrieve the cross-validation R2
#' @examples
#' \donttest{
#' library(h2o)
#'
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#'
#' m <- h2o.glm(x=2:5,y=1,training_frame=fr)
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
#' \donttest{
#' library(h2o)
#'
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#'
#' m <- h2o.deeplearning(x=2:5,y=1,training_frame=fr)
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
#'          GINI coefficient, and \code{\link{h2o.metric}} for the various. See
#'          \code{\link{h2o.performance}} for creating H2OModelMetrics objects.
#'          threshold metrics.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' hex <- h2o.uploadFile(prosPath)
#'
#' hex[,2] <- as.factor(hex[,2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = hex, distribution = "bernoulli")
#' perf <- h2o.performance(model, hex)
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
#' Return the coefficients that can be applied to the non-standardized data.
#'
#' Note: standardize = True by default. If set to False, then coef() returns the coefficients that are fit directly.
#'
#' @param object an \linkS4class{H2OModel} object.
#' @export
h2o.coef <- function(object) {
  if (is(object, "H2OModel")) {
    if (is.null(object@model$coefficients_table)) stop("Can only extract coefficeints from GLMs")
    if (object@allparameters$family != "multinomial" && object@allparameters$family != "ordinal") {
      coefs <- object@model$coefficients_table$coefficients
      names(coefs) <- object@model$coefficients_table$names
    } else {
      coefs <- object@model$coefficients_table
    }
    return(coefs)
  } else {
    stop("Can only extract coefficients from GLMs")
  }  
}

#'
#' Return coefficients fitted on the standardized data (requires standardize = True, which is on by default). These coefficients can be used to evaluate variable importance.
#'
#' @param object an \linkS4class{H2OModel} object.
#' @export
h2o.coef_norm <- function(object) {
  if (is(object, "H2OModel")) {
    if (is.null(object@model$coefficients_table)) stop("Can only extract coefficeints from GLMs")
    if (object@parameters$family != "multinomial"  && object@parameters$family != "ordinal") {
      coefs <- object@model$coefficients_table$standardized_coefficients
      names(coefs) <- object@model$coefficients_table$names
    } else {
      coefs <- object@model$coefficients_table
    }
    return(coefs)
  } else {
    stop("Can only extract coefficients from GLMs")
  }
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
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' hex <- h2o.uploadFile(prosPath)
#'
#' hex[,2] <- as.factor(hex[,2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = hex, distribution = "bernoulli")
#' perf <- h2o.performance(model, hex)
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
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' hex <- h2o.uploadFile(prosPath)
#'
#' hex[,2] <- as.factor(hex[,2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = hex, distribution = "bernoulli")
#' perf <- h2o.performance(model, hex)
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
#' \donttest{
#' library(h2o)
#'
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#'
#' m <- h2o.deeplearning(x=2:5,y=1,training_frame=fr)
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
#' \donttest{
#' library(h2o)
#'
#' h <- h2o.init()
#' fr <- as.h2o(iris)
#'
#' m <- h2o.deeplearning(x=2:5,y=1,training_frame=fr)
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
#' Retrieve the variable importance.
#'
#' @param object An \linkS4class{H2OModel} object.
#' @export
h2o.varimp <- function(object) {
  o <- object
  if( is(o, "H2OModel") ) {
    vi <- o@model$variable_importances
    if( is.null(vi) ) { vi <- object@model$standardized_coefficient_magnitudes }  # no true variable importances, maybe glm coeffs? (return standardized table...)
    if( is.null(vi) ) {
      warning("This model doesn't have variable importances", call. = FALSE)
      return(invisible(NULL))
    }
    vi
  } else {
    warning( paste0("No variable importances for ", class(o)) )
    return(NULL)
  }
}

#'
#' Retrieve Model Score History
#'
#' @param object An \linkS4class{H2OModel} object.
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
#' Retrieve the respective weight matrix
#'
#' @param object An \linkS4class{H2OModel} or \linkS4class{H2OModelMetrics}
#' @param matrix_id An integer, ranging from 1 to number of layers + 1, that specifies the weight matrix to return.
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
#' @param metric (Optional) A specified paramter to retrieve.
#' @return Returns either a single value, or a list of values.
#' @seealso \code{\link{h2o.auc}} for AUC, \code{\link{h2o.giniCoef}} for the
#'          GINI coefficient, and \code{\link{h2o.mse}} for MSE. See
#'          \code{\link{h2o.performance}} for creating H2OModelMetrics objects.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' hex <- h2o.uploadFile(prosPath)
#'
#' hex[,2] <- as.factor(hex[,2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = hex, distribution = "bernoulli")
#' perf <- h2o.performance(model, hex)
#' h2o.F1(perf)
#' }
#' @export
h2o.metric <- function(object, thresholds, metric) {
  if(!is(object, "H2OModelMetrics")) stop(paste0("No ", metric, " for ",class(object)," .Should be a H2OModelMetrics object!"))
  if(is(object, "H2OBinomialMetrics")){
    if(!missing(thresholds)) lapply(thresholds, function(t,object,metric) h2o.find_row_by_threshold(object, t)[, metric], object, metric)
    else {
     if(missing(metric)) object@metrics$thresholds_and_metric_scores else object@metrics$thresholds_and_metric_scores[, c("threshold", metric)]
    }
  }
  else{
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
  h2o.metric(object, thresholds, "error")
}

#' @rdname h2o.metric
#' @export
h2o.maxPerClassError <- function(object, thresholds){
  1.0-h2o.metric(object, thresholds, "min_per_class_accuracy")
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
#' @export
h2o.find_threshold_by_max_metric <- function(object, metric) {
  if(!is(object, "H2OBinomialMetrics")) stop(paste0("No ", metric, " for ",class(object)))
  max_metrics <- object@metrics$max_criteria_and_metric_scores
  max_metrics[match(paste0("max ",metric),max_metrics$metric),"threshold"]
}

#' Find the threshold, give the max metric. No duplicate thresholds allowed
#'
#' @rdname h2o.find_row_by_threshold
#' @param object H2OBinomialMetrics
#' @param threshold number between 0 and 1
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
#' @export
h2o.centers <- function(object) { as.data.frame(object@model$centers[,-1]) }

#'
#' Retrieve the Model Centers STD
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
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
#' @param \dots further arguments to be passed on (currently unimplemented)
#' @export
h2o.num_iterations <- function(object) { object@model$model_summary$number_of_iterations }

#'
#' Retrieve centroid statistics
#'
#' Retrieve the centroid statistics.
#' If "train", "valid", and "xval" parameters are FALSE (default), then the training centroid stats value is returned. If more
#' than one parameter is set to TRUE, then a named list of centroid stats data frames are returned, where the names are "train", "valid"
#' or "xval".
#'
#' @param object An \linkS4class{H2OClusteringModel} object.
#' @param train Retrieve the training centroid statistics
#' @param valid Retrieve the validation centroid statistics
#' @param xval Retrieve the cross-validation centroid statistics
#' @export
h2o.centroid_stats <- function(object, train=FALSE, valid=FALSE, xval=FALSE) {
  model.parts <- .model.parts(object)
  if ( !train && !valid && !xval ) return( model.parts$tm@metrics$centroid_stats )
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
  if ( xval ) {
    if( is.null(model.parts$xm) ) invisible(.warn.no.cross.validation())
    else {
      v[[length(v)+1]] <- model.parts$xm@metrics$centroid_stats
      v_names <- c(v_names,"xval")
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
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' hex <- h2o.uploadFile(prosPath)
#' hex[,2] <- as.factor(hex[,2])
#' model <- h2o.gbm(x = 3:9, y = 2, distribution = "bernoulli",
#'                  training_frame = hex, validation_frame = hex, nfolds=3)
#' h2o.gainsLift(model)              ## extract training metrics
#' h2o.gainsLift(model, valid=TRUE)  ## extract validation metrics (here: the same)
#' h2o.gainsLift(model, xval =TRUE)  ## extract cross-validation metrics
#' h2o.gainsLift(model, newdata=hex) ## score on new data (here: the same)
#' # Generating a ModelMetrics object
#' perf <- h2o.performance(model, hex)
#' h2o.gainsLift(perf)               ## extract from existing metrics object
#' }
#' @export
setGeneric("h2o.gainsLift", function(object, ...) {})

#' @rdname h2o.gainsLift
#' @export
setMethod("h2o.gainsLift", "H2OModel", function(object, newdata, valid=FALSE, xval=FALSE,...) {
  model.parts <- .model.parts(object)
  if( missing(newdata) ) {
    if( valid ) {
      if( is.null(model.parts$vm) ) return( invisible(.warn.no.validation()) )
      else                          return( h2o.gainsLift(model.parts$vm) )
    }
    if ( xval ) {
      if( is.null(model.parts$xm) ) return( invisible(.warn.no.cross.validation()))
      else                          return( h2o.gainsLift(model.parts$xm) )
    }
    return( h2o.gainsLift(model.parts$tm) )
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
#' @param ... Extra arguments for extracting train or valid confusion matrices.
#' @return Calling this function on \linkS4class{H2OModel} objects returns a
#'         confusion matrix corresponding to the \code{\link{predict}} function.
#'         If used on an \linkS4class{H2OBinomialMetrics} object, returns a list
#'         of matrices corresponding to the number of thresholds specified.
#' @seealso \code{\link{predict}} for generating prediction frames,
#'          \code{\link{h2o.performance}} for creating
#'          \linkS4class{H2OModelMetrics}.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' hex <- h2o.uploadFile(prosPath)
#' hex[,2] <- as.factor(hex[,2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = hex, distribution = "bernoulli")
#' h2o.confusionMatrix(model, hex)
#' # Generating a ModelMetrics object
#' perf <- h2o.performance(model, hex)
#' h2o.confusionMatrix(perf)
#' }
#' @export
setGeneric("h2o.confusionMatrix", function(object, ...) {})

#' @rdname h2o.confusionMatrix
#' @export
setMethod("h2o.confusionMatrix", "H2OModel", function(object, newdata, valid=FALSE, ...) {
  model.parts <- .model.parts(object)
  if( missing(newdata) ) {
    if( valid ) {
      if( is.null(model.parts$vm) ) return( invisible(.warn.no.validation()) )
      else                          return( h2o.confusionMatrix(model.parts$vm, ...) )
    } else                          return( h2o.confusionMatrix(model.parts$tm, ...) )
  } else if( valid ) stop("Cannot have both `newdata` and `valid=TRUE`", call.=FALSE)

  # ok need to score on the newdata
  url <- paste0("Predictions/models/",object@model_id, "/frames/", h2o.getId(newdata))
  res <- .h2o.__remoteSend(url, method="POST")

  # Make the correct class of metrics object
  metrics <- new(sub("Model", "Metrics", class(object)), algorithm=object@algorithm, metrics= res$model_metrics[[1L]])   # FIXME: don't think model metrics come out of Predictions anymore!!!
  h2o.confusionMatrix(metrics, ...)
})

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
      else thresholds_list = list(thresholds)
  }

  # error check the metrics_list and thresholds_list
  if( !all(sapply(thresholds_list, f <- function(x) is.numeric(x) && x >= 0 && x <= 1)) )
    stop("All thresholds must be numbers between 0 and 1 (inclusive).")
  allowable_metrics <- c("min_per_class_accuracy", "absolute_mcc", "tnr", "fnr", "fpr", "tpr","precision", "accuracy", "f0point5", "f2", "f1")
  if( !all(sapply(metrics_list, f <- function(x) x %in% allowable_metrics)) )
      stop(paste("The only allowable metrics are ", paste(allowable_metrics, collapse=', ')))

  # make one big list that combines the thresholds and metric-thresholds
  metrics_thresholds = lapply(metrics_list, f <- function(x) h2o.find_threshold_by_max_metric(object, x))
  thresholds_list <- append(thresholds_list, metrics_thresholds)

  thresh2d <- object@metrics$thresholds_and_metric_scores
  actual_thresholds <- thresh2d$threshold
  d <- object@metrics$domain
  m <- lapply(thresholds_list,function(t) {
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
        m <- metrics_list[which(t == metrics_thresholds)]
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
#' \donttest{
#' if (requireNamespace("mlbench", quietly=TRUE)) {
#'     library(h2o)
#'     h2o.init()
#'     
#'     df <- as.h2o(mlbench::mlbench.friedman1(10000,1))
#'     rng <- h2o.runif(df, seed=1234)
#'     train <- df[rng<0.8,]
#'     valid <- df[rng>=0.8,]
#'     
#'     gbm <- h2o.gbm(x = 1:10, y = "y", training_frame = train, validation_frame = valid,
#'                    ntrees=500, learn_rate=0.01, score_each_iteration = TRUE)
#'     plot(gbm)
#'     plot(gbm, timestep = "duration", metric = "deviance")
#'     plot(gbm, timestep = "number_of_trees", metric = "deviance")
#'     plot(gbm, timestep = "number_of_trees", metric = "rmse")
#'     plot(gbm, timestep = "number_of_trees", metric = "mae")
#' }
#' }
#' @export
plot.H2OModel <- function(x, timestep = "AUTO", metric = "AUTO", ...) {
  df <- as.data.frame(x@model$scoring_history)

  #Ensure metric and timestep can be passed in as upper case (by converting to lower case) if not "AUTO"
  if(metric != "AUTO"){
    metric = tolower(metric)
  }

  if(timestep != "AUTO"){
    timestep = tolower(timestep)
  }

  # Separate functionality for GLM since output is different from other algos
  if (x@algorithm == "glm") {
    # H2OBinomialModel and H2ORegressionModel have the same output
    # Also GLM has only one timestep option, which is `iteration`
    timestep <- "iteration"
    if (metric == "AUTO") {
      metric <- "log_likelihood"
    } else if (!(metric %in% c("log_likelihood", "objective"))) {
      stop("for GLM, metric must be one of: log_likelihood, objective")
    }
    graphics::plot(df$iteration, df[,c(metric)], type="l", xlab = timestep, ylab = metric, main = "Validation Scoring History", ...)
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
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' hex <- h2o.importFile(prosPath)
#' hex[,2] <- as.factor(hex[,2])
#' model <- h2o.gbm(x = 3:9, y = 2, training_frame = hex, distribution = "bernoulli")
#' h2o.varimp_plot(model)
#'
#' # for deep learning set the variable_importance parameter to TRUE
#' iris.hex <- as.h2o(iris)
#' iris.dl <- h2o.deeplearning(x = 1:4, y = 5, training_frame = iris.hex,
#' variable_importances = TRUE)
#' h2o.varimp_plot(iris.dl)
#' }
#' @export
h2o.varimp_plot <- function(model, num_of_features = NULL){
  # if glm use h2o.std_coef_plot() instead to get std. coef. magnitudes
  if(model@algorithm[1] == 'glm') {h2o.std_coef_plot(model, num_of_features = num_of_features )}
  else{
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
#' \donttest{
#' library(h2o)
#' h2o.init()
#'
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.importFile(prosPath)
#' prostate.hex[,2] <- as.factor(prostate.hex[,2])
#' prostate.glm <- h2o.glm(y = "CAPSULE", x = c("AGE","RACE","PSA","DCAPS"),
#'                          training_frame = prostate.hex, family = "binomial",
#'                          nfolds = 0, alpha = 0.5, lambda_search = FALSE)
#' h2o.std_coef_plot(prostate.glm)
#' }
#' @export
h2o.std_coef_plot <- function(model, num_of_features = NULL){
  # check that the model is a glm
  if(model@algorithm[1] != "glm") stop("Warning: model must be a GLM")

  # get the coefficients table
  coeff_table_complete <- model@model$coefficients_table
  # remove the intercept row from the complete coeff_table_complete
  coeff_table <- coeff_table_complete[coeff_table_complete$names != "Intercept",]
  # order the coeffcients table by the absolute value of the standardized_coefficients
  sorted_table <- coeff_table[order(abs(coeff_table$standardized_coefficients)),]

  # get a vector of normalized coefs. and abs norm coefs., and the corresponding labels
  norm_coef <- sorted_table$standardized_coefficients
  sort_norm <- abs(sorted_table$standardized_coefficients)
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
            xlim = c(0,1),
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
        xlim = c(0,1),
        main = "Standardized Coef. Magnitudes")
  }

  # add legend, that adapts if one to all bars are plotted
  legend('bottomright', legend = unique(tail(color_sign, n = num_of_features)),
  col = unique(tail(color_code, n = num_of_features)), pch = 20)

}

#' @export
plot.H2OBinomialMetrics <- function(x, type = "roc", main, ...) {
  # TODO: add more types (i.e. cutoffs)
  if(!type %in% c("roc")) stop("type must be 'roc'")
  if(type == "roc") {
    xaxis <- "False Positive Rate"; yaxis = "True Positive Rate"
    if(missing(main)) {
      main <- paste(yaxis, "vs", xaxis)
      if(x@on_train) {
        main <- paste(main, "(on train)")
      } else if (x@on_valid) {
        main <- paste(main, "(on valid)")
      }
    }
    graphics::plot(x@metrics$thresholds_and_metric_scores$fpr, x@metrics$thresholds_and_metric_scores$tpr, main = main, xlab = xaxis, ylab = yaxis, ylim=c(0,1), xlim=c(0,1), ...)
    graphics::abline(0, 1, lty = 2)
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
#' \donttest{
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
#' @seealso \code{link{h2o.tabulate}}
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' df <- as.h2o(iris)
#' tab <- h2o.tabulate(data = df, x = "Sepal.Length", y = "Petal.Width",
#'              weights_column = NULL, nbins_x = 10, nbins_y = 10)
#' plot(tab)              
#' }
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
#' @param data An H2OFrame object used for scoring and constructing the plot.
#' @param cols Feature(s) for which partial dependence will be calculated.
#' @param destination_key An key reference to the created partial dependence tables in H2O.
#' @param nbins Number of bins used. For categorical columns make sure the number of bins exceed the level count.
#' @param plot A logical specifying whether to plot partial dependence table.
#' @param plot_stddev A logical specifying whether to add std err to partial dependence plot.
#' @return Plot and list of calculated mean response tables for each feature requested.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prostate.path <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(path = prostate.path, destination_frame = "prostate.hex")
#' prostate.hex[, "CAPSULE"] <- as.factor(prostate.hex[, "CAPSULE"] )
#' prostate.hex[, "RACE"] <- as.factor(prostate.hex[,"RACE"] )
#' prostate.gbm <- h2o.gbm(x = c("AGE","RACE"),
#'                        y = "CAPSULE",
#'                        training_frame = prostate.hex,
#'                        ntrees = 10,
#'                        max_depth = 5,
#'                        learn_rate = 0.1)
#' h2o.partialPlot(object = prostate.gbm, data = prostate.hex, cols = c("AGE", "RACE"))
#' }
#' @export

h2o.partialPlot <- function(object, data, cols, destination_key, nbins=20, plot = TRUE, plot_stddev = TRUE) {
  if(!is(object, "H2OModel")) stop("object must be an H2Omodel")
  if( is(object, "H2OMultinomialModel")) stop("object must be a regression model or binary classfier")
  if( is(object, "H2OOrdinalModel")) stop("object must be a regression model or binary classfier")
  if(!is(data, "H2OFrame")) stop("data must be H2OFrame")
  if(!is.numeric(nbins) | !(nbins > 0) ) stop("nbins must be a positive numeric")
  if(!is.logical(plot)) stop("plot must be a logical value")
  if(!is.logical(plot_stddev)) stop("plot must be a logical value")
  if(missing(cols)) cols =  object@parameters$x

  y = object@parameters$y
  x = cols
  args <- .verify_dataxy(data, x, y)

  parms = list()
  parms$cols <- paste0("[", paste (args$x, collapse = ','), "]")
  parms$model_id  <- attr(object, "model_id")
  parms$frame_id <- attr(data, "id")
  parms$nbins <- nbins
  if(!missing(destination_key)) parms$destination_key = destination_key

  res <- .h2o.__remoteSend(method = "POST", h2oRestApiVersion = 3, page = "PartialDependence/", .params = parms)
  .h2o.__waitOnJob(res$key$name)
  url <- gsub("/3/", "", res$dest$URL)
  res <- .h2o.__remoteSend(url, method = "GET", h2oRestApiVersion = 3)

  ## Change feature names to the original supplied, the following is okay because order is preserved
  pps <- res$partial_dependence_data
  for(i in 1:length(pps)) if(!all(is.na( pps[[i]])) ) names(pps[[i]]) <- c(cols[i], "mean_response", "stddev_response")

  col_types = unlist(h2o.getTypes(data))
  col_names = names(data)
  pp.plot <- function(pp) {
    if(!all(is.na(pp))) {
      type = col_types[which(col_names == names(pp)[1])]
      if(type == "enum") pp[,1] = factor( pp[,1], levels=pp[,1])
      
      ## Plot one standard deviation above and below the mean
      if( plot_stddev) {
        ## Added upper and lower std dev confidence bound
        upper = pp[,2] + pp[,3]
        lower = pp[,2] - pp[,3]
        plot(pp[,1:2], type = "l", main = attr(x,"description"), ylim  = c(min(lower), max(upper)))

        polygon(c(pp[,1], rev(pp[,1])), c(lower, rev(upper)), col = "grey75", border = F)
        lines(pp[,1], pp[,2], lwd = 2)
        lines(pp[,1], lower, col = "blue", lty = 2)
        lines(pp[,1], upper, col = "blue", lty = 2) 
      } else {
        plot(pp[,1:2], type = "l", main = attr(x,"description") )
      }
    } else {
      print("Partial Dependence not calculated--make sure nbins is as high as the level count")
    }
  }

  if(plot) lapply(pps, pp.plot)
  if(length( pps) == 1) {
    return(pps[[1]])
  } else {
    return(pps)
  }
}

#' Feature Generation via H2O Deep Learning or DeepWater Model
#'
#' Extract the non-linear feature from an H2O data set using an H2O deep learning
#' model.
#' @param object An \linkS4class{H2OModel} object that represents the deep
#' learning model to be used for feature extraction.
#' @param data An H2OFrame object.
#' @param layer Index (for DeepLearning, integer) or Name (for DeepWater, String) of the hidden layer to extract
#' @return Returns an H2OFrame object with as many features as the
#'         number of units in the hidden layer of the specified index.
#' @seealso \code{link{h2o.deeplearning}} for making H2O Deep Learning models.
#' @seealso \code{link{h2o.deepwater}} for making H2O DeepWater models.
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.init()
#' prosPath = system.file("extdata", "prostate.csv", package = "h2o")
#' prostate.hex = h2o.importFile(path = prosPath)
#' prostate.dl = h2o.deeplearning(x = 3:9, y = 2, training_frame = prostate.hex,
#'                                hidden = c(100, 200), epochs = 5)
#' prostate.deepfeatures_layer1 = h2o.deepfeatures(prostate.dl, prostate.hex, layer = 1)
#' prostate.deepfeatures_layer2 = h2o.deepfeatures(prostate.dl, prostate.hex, layer = 2)
#' head(prostate.deepfeatures_layer1)
#' head(prostate.deepfeatures_layer2)
#'
#' #if (h2o.deepwater.available()) {
#' #  prostate.dl = h2o.deepwater(x = 3:9, y = 2, backend="mxnet", training_frame = prostate.hex,
#' #                              hidden = c(100, 200), epochs = 5)
#' #  prostate.deepfeatures_layer1 =
#' #    h2o.deepfeatures(prostate.dl, prostate.hex, layer = "fc1_w")
#' #  prostate.deepfeatures_layer2 =
#' #    h2o.deepfeatures(prostate.dl, prostate.hex, layer = "fc2_w")
#' #  head(prostate.deepfeatures_layer1)
#' #  head(prostate.deepfeatures_layer2)
#' #}
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
