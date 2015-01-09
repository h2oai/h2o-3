#'
#' H2O Predict Method
#'
#'

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
