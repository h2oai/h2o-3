setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testMaxRGLMResultFrameModelID <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  Log.info("Build the MaxRGLM model")
  numModel <- 7
  maxrglm_model <- h2o.maxrglm(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel)
  resultFrame <- h2o.result(maxrglm_model)
  bestModelIDs <- maxrglm_model@model$best_model_ids
  for (ind in c(1:numModel)) {
    glmModel <- h2o.getModel(resultFrame[ind, 2])
    predFrame <- h2o.predict(glmModel, bhexFV)
    glmModel2 <- h2o.getModel(bestModelIDs[[ind]]$name)
    predFrame2 <- h2o.predict(glmModel2, bhexFV)
    compareFrames(predFrame, predFrame2, prob=1, tolerance = 1e-6)
  }
}

doTest("MaxRGLM: test result frame and model id", testMaxRGLMResultFrameModelID)
