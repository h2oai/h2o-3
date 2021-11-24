setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testModelSelectionResultFrameModelID <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  Log.info("Build the MaxRGLM model")
  numModel <- 7
  allsubsetsModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel,
  mode="allsubsets")
  resultFrameAllsubsets <- h2o.result(allsubsetsModel)
  bestModelIDsAllsubsets <- allsubsetsModel@model$best_model_ids
  maxrModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel,
    mode="maxr")
  resultFrameMaxr <- h2o.result(maxrModel)
  bestModelIDsMaxr <- maxrModel@model$best_model_ids
  for (ind in c(1:numModel)) {
    glmModelAllsubsets <- h2o.getModel(resultFrameAllsubsets[ind, 2])
    predFrameAllsubsets <- h2o.predict(glmModelAllsubsets, bhexFV)
    glmModel2Allsubsets <- h2o.getModel(bestModelIDsAllsubsets[[ind]]$name)
    predFrame2Allsubsets <- h2o.predict(glmModel2Allsubsets, bhexFV)
    compareFrames(predFrameAllsubsets, predFrame2Allsubsets, prob=1, tolerance = 1e-6)
    
    glmModelMaxr <- h2o.getModel(resultFrameMaxr[ind, 2])
    predFrameMaxr <- h2o.predict(glmModelMaxr, bhexFV)
    glmModel2Maxr <- h2o.getModel(bestModelIDsMaxr[[ind]]$name)
    predFrame2Maxr <- h2o.predict(glmModel2Maxr, bhexFV)
    compareFrames(predFrameAllsubsets, predFrame2Maxr, prob=1, tolerance = 1e-6)
    compareFrames(predFrameAllsubsets, predFrameAllsubsets, prob=1, tolerance = 1e-6)
  }
}

doTest("ModelSelection with allsubsets, maxr: test result frame and model id", testModelSelectionResultFrameModelID)
