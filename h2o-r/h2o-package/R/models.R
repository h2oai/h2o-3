#'
#' Retrieve Model Data
#'
#' After a model is constructed by H2O, R must create a view of the model. All views are backed by S4 objects that
#' subclass the H2OModel object (see classes.R for class specifications).
#'
#' This file contains the set of model getters that fill out and return the appropriate S4 object.

.deeplearning.builder <- function(json, client) {
  new("H2ODeepLearningModel", h2o = client, key = json$key$name, algo= json$algo, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}

.gbm.builder <- function(json, client) {
  new("H2OGBMModel", h2o = client, key = json$key$name, algo= json$algo, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}

.kmeans.builder <- function(json, client) {
  if(NCOL(json$output$centers) == length(json$output$names))
    colnames(json$output$centers) <- json$output$names
  new("H2OKMeansModel", h2o = client, key = json$key$name, algo= json$algo, model = json$output,
      valid = new("H2OFrame", h2o = client, key="NA"))
}

.quantile.builder <- function(json, client) {
  new("H2OQuantileModel", h2o = client, key = json$key$name, algo= json$algo, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}

#' Cross Validate an H2O Model
h2o.crossValidate <- function(model, nfolds, model.type = c("gbm", "glm", "deeplearning"), params, strategy = c("mod1", "random"), ...)
{
  output <- data.frame()
  
  l <- list(...)
  
  if( is.null(l$envir) ) l$envir <- parent.frame()
  
#   params$envir <- l$envir

  if( nfolds < 2 ) stop("`nfolds` must be greater than or equal to 2")
  if( missing(model) & missing(model.type) ) stop("must declare `model` or `model.type`")
  else if( missing(model) )
  {
    if(model.type == "gbm") model.type = "h2o.gbm"
    else if(model.type == "glm") model.type = "h2o.glm"
    else if(model.type == "deeplearninng") model.type = "h2o.deeplearning"
    
    model <- do.call(model.type, c(params, l$envir))
  }
  output[1, "fold_num"] <- -1
  output[1, "model_key"] <- model@key
  # output[1, "model"] <- model@model$mse_valid
  
  data <- params$training_frame
  data <- eval(data, l$envir)
  data.len <- nrow(data)

  # nfold_vec <- h2o.sample(fr, 1:nfolds)
  nfold_vec <- sample(rep(1:nfolds, length.out = data.len), data.len)

  fnum_id <- as.h2o(conn, nfold_vec)
  fnum_id <- h2o.cbind(fnum_id, data)

  xval <- lapply(1:nfolds, function(i) {
      params$training_frame <- data[fnum_id$object != i, ]
      params$validation_frame <- data[fnum_id$object != i, ]
      fold <- do.call(model.type, c(params, l$envir))
      output[(i+1), "fold_num"] <<- i - 1
      output[(i+1), "model_key"] <<- fold@key
      output[(i+1), "cv_err"] <<- mean(as.vector(fold@model$mse_valid))
      fold
    })
  print(output)
  
  model@xval <- xval
  model
}

