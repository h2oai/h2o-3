setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testMaxRGLMcv <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X   <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  
  Log.info("Build the MaxRGLM model")
  maxrglm_model <- h2o.maxrglm(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, nfolds=2)
  bestR2Value <- h2o.get_best_r2_values(maxrglm_model)
  maxrglm_model2 <- h2o.maxrglm(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, nfolds=2, fold_assignment="random")
  bestR2Value2 <- h2o.get_best_r2_values(maxrglm_model2)
  expect_equal(bestR2Value, bestR2Value2, tol=1e-6)
}

doTest("MaxRGLM: Gaussian data with cross-validation", testMaxRGLMcv)
