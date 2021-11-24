setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testMaxRGLMCoeffs <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  Log.info("Build the MaxRGLM model")
  numModel <- 7
  maxrglmModel <- h2o.maxrglm(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel)
  coeffs <- h2o.coef(maxrglmModel)
  coeffsNorm <- h2o.coef_norm(maxrglmModel)
  numModel = length(coeffs)
  # check coefficients obtained in different ways are the same.
  for (ind in c(1:numModel)) {
    coeffsModel <- coeffs[[ind]]
    coeffsNormModel <- coeffsNorm[[ind]]
    coeffsTemp <- h2o.coef(h2o.getModel(maxrglmModel@model$best_model_ids[[ind]]$name))
    coeffsNormTemp <- h2o.coef_norm(h2o.getModel(maxrglmModel@model$best_model_ids[[ind]]$name))
    expect_equal(coeffsTemp, coeffsModel, tolerance=1e-6)
    expect_equal(coeffsNormTemp, coeffsNormModel, tolerance=1e-6)
  }
}

doTest("MaxRGLM: test h2o.coef() and h2o.coef_norm()", testMaxRGLMCoeffs)
