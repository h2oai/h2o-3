setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testMaxRGLMV <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  bhexFV2 <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X   <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  browser()
  Log.info("Build the MaxRGLM model")
  maxrglm_model <- h2o.maxrglm(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2)
  bestR2Value <- h2o.get_best_r2_values(maxrglm_model)
  maxrglm_modelv <- h2o.maxrglm(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=3, validation_frame = bhexFV2)
  bestR2Valuev <- h2o.get_best_r2_values(maxrglm_modelv) 
  
  expect_equal(bestR2Value, bestR2Valuev[1:2], tol=1e-6)
}

doTest("MaxRGLM: Gaussian data with validation", testMaxRGLMV)
