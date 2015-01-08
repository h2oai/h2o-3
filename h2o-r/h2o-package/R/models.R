#'
#' Retrieve Model Data
#'
#' After a model is constructed by H2O, R must create a view of the model. All views are backed by S4 objects that
#' subclass the H2OModel object (see classes.R for class specifications).
#'
#' This file contains the set of model getters that fill out and return the appropriate S4 object.

#-----------------------------------------------------------------------------------------------------------------------
#
#       KMeans Model Getter
#
#-----------------------------------------------------------------------------------------------------------------------

.kmeans.builder <- function(json, client) {
  if(NCOL(json$output$centers) == length(json$output$names))
    colnames(json$output$centers) <- json$output$names
  new("H2OKMeansModel", h2o = client, key = json$key$name, algo= json$algo, model = json$output,
      valid = new("H2OFrame", h2o = client, key="NA"))
}

#-----------------------------------------------------------------------------------------------------------------------
#
#       GBM Model Getter
#
#-----------------------------------------------------------------------------------------------------------------------

.gbm.builder <- function(json, client) {
  new("H2OGBMModel", h2o = client, key = json$key$name, algo= json$algo, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}

.glm.builder <- function(json, client) {
  new("H2OGLMModel", h2o = client, key = json$key$name, algo= json$algo, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}

.deeplearning.builder <- function(json, client) {
  new("H2ODeepLearningModel", h2o = client, key = json$key$name, algo= json$algo, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}

.quantile.builder <- function(json, client) {
  new("H2OQuantileModel", h2o = client, key = json$key$name, algo= json$algo, model = json$output,
      valid = new("H2OFrame", h2o=client, key="NA"), xval = list())
}