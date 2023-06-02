setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testModelSelection <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X   <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  
  Log.info("Build the MaxRGLM model")
  allsubsetsModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, 
  mode="allsubsets")
  bestR2ValueAllsubsets <- h2o.get_best_r2_values(allsubsetsModel)
  bestPredictorNamesAllsubsets <- h2o.get_best_model_predictors(allsubsetsModel)
  maxrModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, mode="maxr")
  bestR2ValueMaxr <- h2o.get_best_r2_values(maxrModel)
  maxrsweepModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, 
                                       mode="maxrsweep", build_glm_model=TRUE)
  bestR2ValueMaxrsweep <- h2o.get_best_r2_values(maxrsweepModel)
  maxrsweepModelGLM <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, mode="maxrsweep")
  bestR2ValueMaxrsweepGLM <- h2o.get_best_r2_values(maxrsweepModelGLM)
  maxrsweepModelMM <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=2, 
                                         mode="maxrsweep", multinode_mode=TRUE)
  bestR2ValueMaxrsweepMM <- h2o.get_best_r2_values(maxrsweepModelMM)
  # check and make sure two predictor model found has the highest R2 value
  pred2List <- list(c("AGE","RACE"),c("AGE","CAPSULE"),c("AGE","DCAPS"),c("AGE","PSA"),c("AGE","VOL"),c("AGE","DPROS"),
  c("RACE","CAPSULE"),c("RACE","DCAPS"),c("RACE","PSA"),c("RACE","VOL"),c("RACE","DPROS"),
  c("CAPSULE","DCAPS"),c("CAPSULE","PSA"),c("CAPSULE","VOL"),c("CAPSULE","DPROS"),
  c("DCAPS","PSA"),c("DCAPS","VOL"),c("DCAPS","DPROS"),
  c("PSA","VOL"),c("PSA","DPROS"),
  c("VOL","DPROS"))

  bestR2 <- c()
  for (pred in pred2List) {
    m <- h2o.glm(y=Y, x=pred, seed=12345, training_frame = bhexFV, family="gaussian", link="identity")
    bestR2 <- c(bestR2, h2o.r2(m))
  }
  expect_true(abs(max(bestR2)-bestR2ValueAllsubsets[2]) < 1e-6)
  expect_true(abs(max(bestR2)-bestR2ValueMaxr[2]) < 1e-6)
  expect_true(abs(bestR2ValueMaxr[2] - bestR2ValueMaxrsweep[2]) < 1e-5)
  expect_true(abs(bestR2ValueMaxr[2] - bestR2ValueMaxrsweepGLM[2]) < 1e-5)
  expect_true(abs(bestR2ValueMaxr[2] - bestR2ValueMaxrsweepMM[2]) < 1e-5)
  print("Model wth best R2 value have predictors:")
  print(bestPredictorNamesAllsubsets[[2]])
}

doTest("ModelSelection with allsubsets, maxr, maxrsweep: Gaussian data", testModelSelection)
