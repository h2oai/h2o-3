setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testModelSelectionResultFrame <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  Log.info("Build the MaxRGLM model")
  numModel <- 7
  allsubsetsModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel,
  mode="allsubsets")
  bestR2Allsubsets <- h2o.get_best_r2_values(allsubsetsModel)
  resultFrameAllsubsets <- h2o.result(allsubsetsModel)
  
  maxrModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel,
  mode="maxr")
  bestR2Maxr <- h2o.get_best_r2_values(maxrModel)
  resultFrameMaxr <- h2o.result(maxrModel)
  for (ind in c(1:numModel)) {
    r2Allsubsets <- bestR2Allsubsets[ind]
    r2FrameAllsubsets <- resultFrameAllsubsets[ind, 3] # get r2
    glmModelAllsubsets <- h2o.getModel(resultFrameAllsubsets[ind, 2])
    predFrameAllsubsets <- h2o.predict(glmModelAllsubsets, bhexFV)
    print(predFrameAllsubsets[1,1])
    r2ModelAllsubsets <- h2o.r2(glmModelAllsubsets)
    expect_equal(r2Allsubsets, r2FrameAllsubsets, tolerance=1e-6)
    expect_equal(r2FrameAllsubsets, r2ModelAllsubsets, tolerance=1e-06)
    
    r2Maxr <- bestR2Maxr[ind]
    r2FrameMaxr <- resultFrameMaxr[ind, 3] # get r2
    glmModelMaxr <- h2o.getModel(resultFrameMaxr[ind, 2])
    predFrameMaxr <- h2o.predict(glmModelMaxr, bhexFV)
    print(predFrameMaxr[1,1])
        r2ModelMaxr <- h2o.r2(glmModelMaxr)
    expect_equal(r2Maxr, r2FrameMaxr, tolerance=1e-6)
    expect_equal(r2FrameMaxr, r2ModelMaxr, tolerance=1e-06)
    expect_equal(r2FrameMaxr, r2ModelAllsubsets, tolerance=1e-06)
  }
}

doTest("ModelSelection with allsubsets, maxr: test result frame", testModelSelectionResultFrame)
