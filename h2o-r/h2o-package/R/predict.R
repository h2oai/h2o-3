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
  if(!inherits(object, types$object)) stop("`object` must be an ", types$object)
  if(missing(newdata)) {
    newdata <- object@data # predicting on data used in train
    warning("predicting on training data")
  }
  if(!inherits(newdata, types$newdata)) stop('`newdata` must be an H2OFrame object')
}

#'
#' Deeplearing H2O Predict Method
#'
#' Predict with an object of class H2ODeepLearningModel
predict.H2ODeepLearningModel <- function(object, newdata, ...) {
  .validate.predict(object, newdata, types=list(object="H2ODeepLearningModel", 
                                                newdata="H2OFrame"))
  # Send keys to create predictions
  url <- .h2o.__PREDICT(object@key, newdata@key)
  res <- .h2o.__remoteSend(object@h2o, url, method = "POST")
  res <- res$model_metrics[[1L]]$predictions
  # Grab info to make data frame
  .h2o.parsedPredData(newdata@h2o, res)
}

#'
#' Gradient Boosting Machine H2O Predict Method
#'
#' Predict with an object of class H2OGBMModel
predict.H2OGBMModel <- function(object, newdata, ...) {
  # Validate that the object is a H2OModel and the newdata is a H2OFrame
  .validate.predict(object, newdata, types = list(object = "H2OGBMModel",
                                                  newdata = "H2OFrame"))
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
  # Validate that the object is a H2OModel and the newdata is a H2OFrame
  .validate.predict(object, newdata, types = list(object = "H2OKMeansModel",
                                                  newdata = "H2OFrame"))
  # Send keys to create predictions
  url <- .h2o.__PREDICT(object@key, newdata@key)
  res <- .h2o.__remoteSend(object@h2o, url, method = "POST")
  res <- res$model_metrics[[1L]]$predictions
  # Grab info to make data frame
  .h2o.parsedPredData(newdata@h2o, res)
}
