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
  # check and make sure two predictor model found has the highest R2 value
  pred2List <- list(c("AGE","RACE"), c("AGE","CAPSULE"), c("AGE","DCAPS"), c("AGE", "PSA"), c("AGE","VOL"),c("AGE", "DPROS"),
                    c("RACE","CAPSULE"),c("RACE","DCAPS"),c("RACE","PSA"),c("RACE","VOL"),c("RACE","DPROS"), c("CAPSULE","DCAPS"),
                    c("CAPSULE","PSA"),c("CAPSULE","VOL"),c("CAPSULE","DPROS"), c("DCAPS","PSA"),c("DCAPS","VOL"),c("DCAPS","DPROS"),
                    c("PSA","VOL"),c("PSA","DPROS"), c("VOL","DPROS"))
  pred2List <- list(c("AGE","RACE"),c("AGE","CAPSULE"),c("AGE","DCAPS"),c("AGE","PSA"),c("AGE","VOL"),c("AGE","DPROS"),
  c("RACE","CAPSULE"),c("RACE","DCAPS"),c("RACE","PSA"),c("RACE","VOL"),c("RACE","DPROS"),
  c("CAPSULE","DCAPS"),c("CAPSULE","PSA"),c("CAPSULE","VOL"),c("CAPSULE","DPROS"),
  c("DCAPS","PSA"),c("DCAPS","VOL"),c("DCAPS","DPROS"),
  c("PSA","VOL"),c("PSA","DPROS"),
  c("VOL","DPROS"))
  pred2list <- list(c("CAPSULE", "AGE"), c("CAPSULE", "RACE"), c("CAPSULE", "DPROS"), c("CAPSULE", "DCAPS"), c("CAPSULE", "PSA"), c("CAPSULE", "VOL"),
                    c("AGE", "RACE"), c("AGE", "DPROS"), c("AGE", "DCAPS"), c("AGE", "PSA"), c("AGE", "VOL"),
                    c("RACE", "DPROS"), c("RACE", "DCAPS"), c("RACE", "PSA"), c("RACE", "VOL"),
                    c("DPROS", "DCAPS"), c("DPROS", "PSA"), c("DPROS", "VOL"),
                    c("DCAPS", "PSA"), c("DCAPS", "VOL"),
                    c("PSA", "VOL"))
  bestR2 <- c()
  for (pred in pred2List) {
    m <- h2o.glm(y=Y, x=pred, seed=12345, training_frame = bhexFV, family="gaussian", link="identity")
    bestR2 <- c(bestR2, h2o.r2(m))
  }
  expect_true(abs(max(bestR2)-bestR2ValueAllsubsets[2]) < 1e-6)
  expect_true(abs(max(bestR2)-bestR2ValueMaxr[2]) < 1e-6)
  print("Model wth best R2 value have predictors:")
  print(bestPredictorNamesAllsubsets[[2]])
}

doTest("ModelSelection with allsubsets, maxr: Gaussian data", testModelSelection)
