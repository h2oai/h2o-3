setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testModelSelectionCoeffs <- function() {
  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/prostate.csv"))
  Y <- "GLEASON"
  X <- c("AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS")
  Log.info("Build the modelSelection models")
  numModel <- 7
  allsubsetsModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel,
  mode="allsubsets")
  resultAllsubsets <- h2o.result(allsubsetsModel) # H2OFrame containing best model_ids, best_r2_value, predictor subsets
  print(resultAllsubsets)
  coeffsAllsubsets <- h2o.coef(allsubsetsModel) # list of coefficients from model built from each predictor size
  print(coeffsAllsubsets)
  coeffsNormAllsubsets <- h2o.coef_norm(allsubsetsModel) # list of standardized coefficients from model built from each predictor size
  print(coeffsNormAllsubsets)
  
  maxrModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, max_predictor_number=numModel,
    mode="maxr")
  resultMaxr <- h2o.result(maxrModel) # H2OFrame containing best model_ids, best_r2_value, predictor subsets
  print(resultMaxr)
  coeffsMaxr <- h2o.coef(maxrModel) # list of coefficients from model built from each predictor size
  print(coeffsMaxr)
  coeffsNormMaxr <- h2o.coef_norm(maxrModel) # list of standardized coefficients from model built from each predictor size
  print(coeffsNormMaxr)
  for (index in seq(1,length(coeffsNormAllsubsets))) {
    oneModelCoeffAllsubsets <- h2o.coef(allsubsetsModel, index)
    oneModelCoeffNormAllsubsets <- h2o.coef_norm(allsubsetsModel, index)
    expect_equal(coeffsAllsubsets[[index]], oneModelCoeffAllsubsets, tolerance=1e-6)
    expect_equal(coeffsNormAllsubsets[[index]], oneModelCoeffNormAllsubsets, tolerance=1e-6)
    
    oneModelCoeffMaxr <- h2o.coef(maxrModel, index)
    oneModelCoeffNormMaxr <- h2o.coef_norm(maxrModel, index)
    expect_equal(coeffsMaxr[[index]], oneModelCoeffMaxr, tolerance=1e-6)
    expect_equal(coeffsNormMaxr[[index]], oneModelCoeffNormMaxr, tolerance=1e-6)
    coefsMaxr <- coeffsNormMaxr[[index]]
    coefsAllsubsets <- coeffsNormAllsubsets[[index]]
    expect_equal(coefsMaxr[order(coefsMaxr)], coefsAllsubsets[order(coefsAllsubsets)], tolerance=1e-6)
  }
}

doTest("ModelSelection with allsubsets, mZE: test h2o.coef() and h2o.coef_norm()", testModelSelectionCoeffs)
