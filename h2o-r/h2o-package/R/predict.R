#'
#' H2O Predict Methods
#'
#'
#' Here are gathered the preponderance of predict methods for the various
#' machine learning models that H2O may construct.
#'


#'
#' Model Predict Endpoint
#'
.h2o.__PREDICT <- function(modelKey, frameKey) {
  paste0('Predictions.json/models/', modelKey, '/frames/', frameKey)
}

#'
#' Validate Predict Parameters
#'
.validate.predict<-
function(object, newdata, types) {
  if(!(object %i% types$object)) stop("`object` must be an ", types$object)
  if(missing(newdata)) {
    newdata <- object@data # predicting on data used in train
    warning("predicting on training data.")
  }
  if(!(newdata %i% types$newdata)) stop('`newdata` must be a H2O dataset')
}

#'
#' Deeplearing H2O Predict Method
#'
#' Predict with an object of class H2ODeepLearningModel
predict.H2ODeepLearningModel <- function(object, newdata, ...) {
  .validate.predict(object, newdata, types=list(object="H2ODeepLearningModel", 
                                                newdata="h2o.frame"))
  # Send keys to create predictions
  url <- .h2o.__PREDICT(object@key, newdata@key)
  res <- .h2o.__remoteSend(object@h2o, url, method = "POST")
  res <- res$model_metrics[[1L]]$predictions
  # Grab info to make data frame
  .h2o.parsedPredData(newdata@h2o, res)
}

#'
#' KMeans H2O Predict Method
#'
#' Predict with an object of class H2OKMeansModel
predict.H2OKMeansModel <- function(object, newdata, ...) {
  # Validate that the object is a h2o.model and the newdata is a h2o.frame
  .validate.predict(object, newdata, types = list(object = "H2OKMeansModel",
                                                  newdata = "h2o.frame"))
  # Send keys to create predictions
  url <- .h2o.__PREDICT(object@key, newdata@key)
  res <- .h2o.__remoteSend(object@h2o, url, method = "POST")
  res <- res$model_metrics[[1L]]$predictions
  # Grab info to make data frame
  .h2o.parsedPredData(newdata@h2o, res)
}

predict.H2OGBMModel <- function(object, newdata, ...) {
  # Validate that the object is a h2o.model and the newdata is a h2o.frame
  .validate.predict(object, newdata, types = list(object = "H2OGBMModel",
                                                  newdata = "h2o.frame"))
  # Send keys to create predictions
  url <- .h2o.__PREDICT(object@key, newdata@key)
  res <- .h2o.__remoteSend(object@h2o, url, method = "POST")
  res <- res$model_metrics[[1L]]$predictions
  # Grab info to make data frame
  .h2o.parsedPredData(newdata@h2o, res)
}

predict.H2OGLMModel <- function(object, newdata, ...) {
  # Validate that the object is a h2o.model and the newdata is a h2o.frame
  .validate.predict(object, newdata, types = list(object = "H2OGLMModel",
                                                  newdata = "h2o.frame"))
  # Send keys to create predictions
  url <- .h2o.__PREDICT(object@key, newdata@key)
  res <- .h2o.__remoteSend(object@h2o, url, method = "POST")
  res <- res$model_metrics[[1]]$predictions
  # Grab info to make data frame
  .h2o.parsedPredData(newdata@h2o, res)
}

#  LEGACY PREDICT BELOW
#h2o.predict <- function(object, newdata, ...) {
#  if(!inherits(object, "h2o.model")) stop("object must be an H2O model")
#  if( missing(newdata) ) newdata <- object@data
#  if(class(newdata) != "h2o.frame") stop('newdata must be a H2O dataset')
#
#  if(class(object) %in% c("H2OCoxPHModel", "H2OGBMModel", "H2OKMeansModel", "H2ODRFModel", "H2ONBModel",
#                          "H2ODeepLearningModel", "H2OSpeeDRFModel")) {
#    # Set randomized prediction key
#    key_prefix = switch(class(object), "H2OCoxPHModel" = "CoxPHPredict", "H2OGBMModel" = "GBMPredict",
#                        "H2OKMeansModel" = "KMeansPredict", "H2ODRFModel" = "DRFPredict",
#                        "H2OGLMModel" = "GLM2Predict", "H2ONBModel" = "NBPredict",
#                        "H2ODeepLearningModel" = "DeepLearningPredict", "H2OSpeeDRFModel" = "SpeeDRFPredict")
#    rand_pred_key = .h2o.__uniqID(key_prefix)
#    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_PREDICT2, model=object@key, data=newdata@key, prediction=rand_pred_key)
##    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_INSPECT2, src_key=rand_pred_key)
#    .h2o.exec2(rand_pred_key, h2o = object@data@h2o, rand_pred_key)
#  } else if(class(object) == "H2OPCAModel") {
#    # Predict with user imposed number of principle components
#    .args <- list(...)
#    numPC = .args$num_pc
#    # Set randomized prediction key
#    rand_pred_key = .h2o.__uniqID("PCAPredict")
#    # Find the number of columns in new data that match columns used to build pca model, detects expanded cols
#    if(is.null(numPC)) numPC = 1
## Taken out so that default numPC = 1 instead of # of principle components resulting from analysis
##    {
##      match_cols <- function(colname) length(grep(pattern = colname , object@model$params$x))
##      numMatch = sum(sapply(colnames(newdata), match_cols))
##      numPC = min(numMatch, object@model$num_pc)
##    }
#    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_PCASCORE, source=newdata@key, model=object@key, destination_key=rand_pred_key, num_pc=numPC)
#    .h2o.__waitOnJob(object@data@h2o, res$job_key)
#    .h2o.exec2(rand_pred_key, h2o = object@data@h2o, rand_pred_key)
#  } else if(class(object) == "H2OGLMModel"){
# # Set randomized prediction key
#    key_prefix = "GLM2Predict"
#    rand_pred_key = .h2o.__uniqID(key_prefix)
#    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_GLMPREDICT2, model=object@key, data=newdata@key, lambda=object@model$lambda,prediction=rand_pred_key)
#    res = .h2o.__remoteSend(object@data@h2o, .h2o.__PAGE_INSPECT2, src_key=rand_pred_key)
#    .h2o.exec2(rand_pred_key, h2o = object@data@h2o, rand_pred_key)
#  } else
#    stop(paste("Prediction has not yet been implemented for", class(object)))
#}