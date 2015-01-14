#'
#' H2O Model Related Functions
#'

# ------------------------------- Helper Functions --------------------------- #
# Used to verify data, x, y and turn into the appropriate things
.verify_dataxy <- function(data, x, y, autoencoder = FALSE) {
  if(!is(data,  "H2OFrame"))
    stop('`data` must be an H2OFrame object')
  if(!is.character(x) && !is.numeric(x))
    stop('`x` must be column names or indices')
  if(!is.character(y) && !is.numeric(y))
    stop('`y` must be a column name or index')

  cc <- colnames(data)

  if(is.character(x)) {
    if(!all(x %in% cc))
      stop("Invalid column names: ", paste(x[!(x %in% cc)], collapse=','))
    x_i <- match(x, cc)
  } else {
    if(any( x < 1L | x > length(cc)))
      stop('out of range explanatory variable ', paste(x[x < 1L | x > length(cc)], collapse=','))
    x_i <- x
    x <- cc[x_i]
  }

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
  if(length(x_ignore) == 0L) x_ignore <- ''
  list(x=x, y=y, x_i=x_i, x_ignore=x_ignore, y_i=y_i)
}

.verify_datacols <- function(data, cols) {
  if(!is(data, "H2OFrame"))
    stop('`data` must be an H2OFrame object')
  if(!is.character(cols) && !is.numeric(cols))
    stop('`cols` must be column names or indices')

  cc <- colnames(data)
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

.h2o.createModel <- function(conn = h2o.getConnection(), algo, params, envir) {
  params$training_frame <- get("training_frame", parent.frame())

  #---------- Force evaluate temporary ASTs ----------#
  delete_train <- !.is.eval(params$training_frame)
  if (delete_train) {
    temp_train_key <- params$training_frame@key
    .force.eval(ast = params$training_frame@ast, h2o.ID = temp_train_key)
  }
  if (!is.null(params$validation_frame)){
    params$validation_frame <- get("validation_frame", parent.frame())
    delete_valid <- !.is.eval(params$validation_frame)
    if (delete_valid) {
      temp_valid_key <- params$validation_frame@key
      .force.eval(ast = params$validation_frame@ast, h2o.ID = temp_valid_key)
    }
  }

  ALL_PARAMS <- .h2o.__remoteSend(conn, method = "GET", .h2o.__MODEL_BUILDERS(algo))$model_builders[[algo]]$parameters

  params <- lapply(as.list(params), function(i) {
                     if (is.name(i))    i <- get(deparse(i), envir)
                     if (is.call(i))    i <- eval(i, envir)
                     if (is.integer(i)) i <- as.numeric(i)
                     i
                   })

  #---------- Check user parameter types ----------#
  error <- lapply(ALL_PARAMS, function(i) {
    e <- ""
    if (i$required && !(i$name %in% names(params)))
      e <- paste0("argument \"", i$name, "\" is missing, with no default\n")
    else if (i$name %in% names(params)) {
      # changing Java types to R types
      mapping <- .type.map[i$type,]
      type    <- mapping[1L, 1L]
      scalar  <- mapping[1L, 2L]
      if (is.na(type))
        stop("Cannot find type ", i$type, " in .type.map")
      if (scalar) {
        if (!inherits(params[[i$name]], type))
          e <- paste0("\"", i$name , "\" must be of type ", type, ", but got ", class(params[[i$name]]), ".\n")
        else if ((length(i$values) > 1L) && !(params[[i$name]] %in% i$values)) {
          e <- paste0("\"", i$name,"\" must be in")
          for (fact in i$values)
            e <- paste0(e, " \"", fact, "\",")
          e <- paste(e, "but got", params[[i$name]])
        }
      } else {
        if (!inherits(params[[i$name]], type))
          e <- paste0("vector of ", i$name, " must be of type ", type, ", but got ", class(params[[i$name]]), ".\n")
        else
          params[[i$name]] <<- .collapse(params[[i$name]])
      }
    }
    e
  })
  if(any(nzchar(error)))
    stop(error)

  #---------- Create parameter list to pass ----------#
  param_values <- lapply(params, function(i) {
    if(is(i, "H2OFrame"))
      i@key
    else
      i
  })

  #---------- Validate parameters ----------#
  validation <- .h2o.__remoteSend(conn, method = "POST", paste0(.h2o.__MODEL_BUILDERS(algo), "/parameters"), .params = param_values)
  if(length(validation$validation_messages) != 0L) {
    error <- lapply(validation$validation_messages, function(i) {
      if( i$message_type == "ERROR" )
        paste0(i$message, ".\n")
      else ""
    })
    if(any(nzchar(error))) stop(error)
    warn <- lapply(validation$validation_messages, function(i) {
      if( i$message_type == "WARN" )
        paste0(i$message, ".\n")
      else ""
    })
    if(any(nzchar(warn))) warning(warn)
  }

  res <- .h2o.__remoteSend(conn, method = "POST", .h2o.__MODEL_BUILDERS(algo), .params = param_values)

  job_key  <- res$job[[1L]]$key$name
  dest_key <- res$jobs[[1L]]$dest$name
  .h2o.__waitOnJob(conn, job_key)

  model <- h2o.getModel(dest_key, conn)

  if (delete_train)
    h2o.rm(temp_train_key)
  if (!is.null(params$validation_frame))
    if (delete_valid)
      h2o.rm(temp_valid_key)

  model
}

predict.H2OModel <- function(object, newdata, ...) {
  if (missing(newdata)) {
    stop("predictions with a missing `newdata` argument is not implemented yet")
  }

  # Send keys to create predictions
  url <- paste0('Predictions.json/models/', object@key, '/frames/', newdata@key)
  res <- .h2o.__remoteSend(object@h2o, url, method = "POST")
  res <- res$model_metrics[[1L]]$predictions

  # Grab info to make data frame
  .h2o.parsedPredData(newdata@h2o, res)
}

#' Cross Validate an H2O Model
h2o.crossValidate <- function(model, nfolds, model.type = c("gbm", "glm", "deeplearning"), params, strategy = c("mod1", "random"), ...)
{
  output <- data.frame()
  dots <- list(...)
  
  for(type in dots)
    if (is.environment(type))
    {
      dots$envir <- type
      type <- NULL
    }
  if (is.null(dots$envir)) 
    dots$envir <- parent.frame()
#   params$envir <- l$envir

  if( nfolds < 2 ) stop("`nfolds` must be greater than or equal to 2")
  if( missing(model) & missing(model.type) ) stop("must declare `model` or `model.type`")
  else if( missing(model) )
  {
    if(model.type == "gbm") model.type = "h2o.gbm"
    else if(model.type == "glm") model.type = "h2o.glm"
    else if(model.type == "deeplearning") model.type = "h2o.deeplearning"
    
    model <- do.call(model.type, c(params, envir = dots$envir))
  }
  output[1, "fold_num"] <- -1
  output[1, "model_key"] <- model@key
  # output[1, "model"] <- model@model$mse_valid
  
  data <- params$training_frame
  data <- eval(data, dots$envir)
  data.len <- nrow(data)

  # nfold_vec <- h2o.sample(fr, 1:nfolds)
  nfold_vec <- sample(rep(1:nfolds, length.out = data.len), data.len)

  fnum_id <- as.h2o(conn, nfold_vec)
  fnum_id <- h2o.cbind(fnum_id, data)

  xval <- lapply(1:nfolds, function(i) {
      params$training_frame <- data[fnum_id$object != i, ]
      params$validation_frame <- data[fnum_id$object != i, ]
      fold <- do.call(model.type, c(params, envir = dots$envir))
      output[(i+1), "fold_num"] <<- i - 1
      output[(i+1), "model_key"] <<- fold@key
      # output[(i+1), "cv_err"] <<- mean(as.vector(fold@model$mse_valid))
      fold
    })
  print(output)
  
  model
}

#' Model Performance Metrics in H2O
#'
#' Given a trained h2o model, compute its performance on the given
#' dataset
#'
#'
#' @param model An \linkS4class{H2OModel} object
#' @param data An \linkS4class{H2OFrame}. The model will make predictions
#'        on this dataset, and subsequently score them. The dataset should
#'        match the dataset that was used to train the model, in terms of
#'        column names, types, and dimensions.
#' @return Returns an object of the \linkS4class{H2OModelMetrics} subclass.
#' @examples
#' library(h2o)
#' localH2O <- h2o.init()
#' prosPath <- system.file("extdata", "prostate.csv", package="h2o")
#' prostate.hex <- h2o.uploadFile(localH2O, path = prosPath)
#' prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
#' prostate.gbm <- h2o.gbm(3:9, "CAPSULE", prostate.hex)
#' h2o.performance(model = prostate.gbm, data=prostate.hex)
h2o.performance <- function(model, data=NULL) {
  # Required args: model
  if(missing(model)) stop ("argument \"model\" is missing, with no default")
  
  # Some parameter checking
  if(!inherits(model, "H2OModel")) stop("model must be of type H2OModel")
  if(!is.null(data) && class(data) != "H2OFrame") stop("data must be an H2OFrame")
  
  
  parms <- list()
  parms[["model"]] <- model@key
  if(!is.null(data))
    parms[["frame"]] <- data@key
  
  if(missing(data)){
    res <- .h2o.__remoteSend(model@h2o, method = "GET", .h2o.__MODEL_METRICS(model@key))
  }
  else {
    res <- .h2o.__remoteSend(model@h2o, method = "POST", .h2o.__MODEL_METRICS(model@key,data@key), .params = parms)
  }
  
  algo <- model@algorithm
  res$model_metrics <- unlist(res$model_metrics, F)
  metrics <- res$model_metrics[!(names(res$model_metrics) %in% c("__meta", "names", "domains", "model_category"))]
  
  model_category <- res$model_metrics$model_category
  Class <- paste0("H2O", model_category, "Metrics")
  
  new(Class       = Class,
      algorithm   = algo,
      metrics = metrics)
}
