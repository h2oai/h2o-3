setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testModelSelectionCoeffs <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  Log.info("Build the MaxRGLM model")
  numModel <- 7
  allsubsetsModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel, 
  mode="allsubsets")
  coeffsAllsubsets <- h2o.coef(allsubsetsModel)
  coeffsNormAllsubsets <- h2o.coef_norm(allsubsetsModel)
  numModel = length(coeffsAllsubsets)
  
  maxrModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel, 
    mode="maxr")
  coeffsMaxr <- h2o.coef(maxrModel)
  coeffsNormMaxr <- h2o.coef_norm(maxrModel)
  # check coefficients obtained in different ways are the same.
  for (ind in c(1:numModel)) {
    coeffsModelAllsubsets <- coeffsAllsubsets[[ind]]
    coeffsNormModelAllsubsets <- coeffsNormAllsubsets[[ind]]
    coeffsTempAllsubsets <- h2o.coef(h2o.getModel(allsubsetsModel@model$best_model_ids[[ind]]$name))
    coeffsNormTempAllsubsets <- h2o.coef_norm(h2o.getModel(allsubsetsModel@model$best_model_ids[[ind]]$name))
    expect_equal(coeffsModelAllsubsets, coeffsTempAllsubsets, tolerance=1e-6)
    expect_equal(coeffsNormModelAllsubsets, coeffsNormTempAllsubsets, tolerance=1e-6)
    
    coeffsModelMaxr <- coeffsMaxr[[ind]]
    coeffsNormModelMaxr <- coeffsNormMaxr[[ind]]
    coeffsTempMaxr <- h2o.coef(h2o.getModel(maxrModel@model$best_model_ids[[ind]]$name))
    coeffsNormTempMaxr <- h2o.coef_norm(h2o.getModel(maxrModel@model$best_model_ids[[ind]]$name))
    expect_equal(coeffsModelMaxr, coeffsTempMaxr, tolerance=1e-6)
    expect_equal(coeffsNormModelMaxr, coeffsNormTempMaxr, tolerance=1e-6)
    expect_equal(coeffsNormModelAllsubsets[order(coeffsNormModelAllsubsets)], coeffsNormTempMaxr[order(coeffsNormTempMaxr)], tolerance=1e-6)
  }
}

doTest("ModelSelection with allsubsets, maxr: test h2o.coef() and h2o.coef_norm()", testModelSelectionCoeffs)
