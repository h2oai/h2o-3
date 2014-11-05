#'
#' H2O Predict Methods
#'
#'
#' Here are gathered the preponderance of predict methods for the various machine
#' learning models that H2O may construct.
#'


#'
#' Validate Predict Parameters
#'
.validate.predict<-
function(object, newdata, types) {
  if( missing(object) ) stop('Must specify `object` as an ' %p0% types$object)
  if(!(object %i% types$object)) stop("`object` must be an " %p0% types$object)
  if( missing(newdata) ) {
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
  .validate(object, newdata, types=list(object="H2ODeepLearningModel", newdata="H2OFrame"))
  key_prefix <- "H2ODeepLearningModel"
  rand_pred_key <- .uniq.id(key_prefix)
#  res <- .h2o.__remoteSend(object@h2o,
}


#  LEGACY PREDICT BELOW
#h2o.predict <- function(object, newdata, ...) {
#  if( missing(object) ) stop('Must specify object')
#  if(!inherits(object, "H2OModel")) stop("object must be an H2O model")
#  if( missing(newdata) ) newdata <- object@data
#  if(class(newdata) != "H2OParsedData") stop('newdata must be a H2O dataset')
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