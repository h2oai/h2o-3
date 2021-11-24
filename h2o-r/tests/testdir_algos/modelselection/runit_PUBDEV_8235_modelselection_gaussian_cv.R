setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testModelSelectionCV <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X   <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  
  Log.info("Build the MaxRGLM model")
  allsubsetsModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, nfolds=2,
   mode="allsubsets")
  bestR2Allsubsets <- h2o.get_best_r2_values(allsubsetsModel)
  allsubsetsModelR <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, nfolds=2,
   fold_assignment="random", mode="allsubsets")
  bestR2AllsubsetsR <- h2o.get_best_r2_values(allsubsetsModelR)
  expect_equal(bestR2Allsubsets, bestR2AllsubsetsR, tol=1e-6)
  
   # checking out maxr mode
   maxrModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, nfolds=2,
     mode="maxr")
   bestR2Maxr <- h2o.get_best_r2_values(maxrModel)
   maxrModelR <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, nfolds=2,
     fold_assignment="random", mode="maxr")
   bestR2MaxrR <- h2o.get_best_r2_values(maxrModelR)
   expect_equal(bestR2Maxr, bestR2MaxrR, tol=1e-6)
   expect_equal(bestR2AllsubsetsR, bestR2MaxrR, tol=1e-6)

}

doTest("ModelSelection with allsubsets, maxr: Gaussian data with cross-validation", testModelSelectionCV)
