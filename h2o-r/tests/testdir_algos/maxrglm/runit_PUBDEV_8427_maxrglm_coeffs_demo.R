setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testMaxRGLMCoeffs <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  Log.info("Build the MaxRGLM model")
  numModel <- 7
  maxrglmModel <- h2o.maxrglm(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel)
  result <- h2o.result(maxrglmModel) # H2OFrame containing best model_ids, best_r2_value, predictor subsets
  print(result)
  coeffs <- h2o.coef(maxrglmModel) # list of coefficients from model built from each predictor size
  print(coeffs)
  coeffsNorm <- h2o.coef_norm(maxrglmModel) # list of standardized coefficients from model built from each predictor size
  print(coeffsNorm)
  for (index in seq(1,length(coeffsNorm))) {
    oneModelCoeff <- h2o.coef(maxrglmModel, index)
    oneModelCoeffNorm <- h2o.coef_norm(maxrglmModel, index)
    expect_equal(coeffs[[index]], oneModelCoeff, tolerance=1e-6)
    expect_equal(coeffsNorm[[index]], oneModelCoeffNorm, tolerance=1e-6)
  }
}

doTest("MaxRGLM: test h2o.coef() and h2o.coef_norm()", testMaxRGLMCoeffs)
