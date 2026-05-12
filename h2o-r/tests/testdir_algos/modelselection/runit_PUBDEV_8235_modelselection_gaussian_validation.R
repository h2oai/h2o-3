setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testModelSelectionV <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  bhexFV2 <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X   <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  
  Log.info("Build the MaxRGLM model")
  allsubsetsModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, 
  mode="allsubsets")
  bestR2Allsubsets <- h2o.get_best_r2_values(allsubsetsModel)
  allsubsetsModelV <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=3, 
  validation_frame = bhexFV2, mode="allsubsets")
  bestR2AllsubsetsV <- h2o.get_best_r2_values(allsubsetsModelV) 
  expect_equal(bestR2Allsubsets, bestR2AllsubsetsV[1:2], tol=1e-6)
  
  maxrModelV <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=3, 
    validation_frame = bhexFV2, mode="maxr")
  bestR2MaxrV <- h2o.get_best_r2_values(maxrModelV) 
  expect_equal(bestR2MaxrV, bestR2AllsubsetsV, tol=1e-6)
}

doTest("ModelSelection with allsubsets, maxr: Gaussian data with validation", testModelSelectionV)
