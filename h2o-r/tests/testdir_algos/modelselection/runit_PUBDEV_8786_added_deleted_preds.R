setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# check we can get added and removed predictors in the result frame and call from model
testAddedRemovedPreds <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  Log.info("Build the MaxRGLM model")
  numModel <- 7
  allsubsetsModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel, mode="allsubsets")
  assertAddedRemovedPreds(allsubsetsModel)
  maxrModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel, mode="maxr")
  assertAddedRemovedPreds(maxrModel)
  maxrsweepModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel, mode="maxrsweep")
  assertAddedRemovedPreds(maxrsweepModel)
  backwardModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, mode="backward")
  assertAddedRemovedPreds(backwardModel)
  maxrsweepModelGLM <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel, mode="maxrsweep", build_glm_model=FALSE)
  assertAddedRemovedPreds(maxrsweepModelGLM)
}

assertAddedRemovedPreds <- function(model) {
  resultFrame <- h2o.result(model)
  removedPF <- resultFrame["predictors_removed"]
  removedPreds <- h2o.get_predictors_removed_per_step(model)
  assertFrameListEquals(removedPF, removedPreds)
  
  if (model@allparameters$mode != 'backward') {
    addPreds <- h2o.get_predictors_added_per_step(model)
    addPredsF <- resultFrame["predictors_added"]
    assertFrameListEquals(addPredsF, addPreds)
  }
}

assertFrameListEquals <- function(colFrame, oneList) {
  numEle <- h2o.nrow(colFrame)
  for (ind in c(1:numEle)) {
    expect_true(oneList[ind] == colFrame[ind,1])
  }
}

doTest("ModelSelection: test added and removed predictors in result frame", testAddedRemovedPreds)
