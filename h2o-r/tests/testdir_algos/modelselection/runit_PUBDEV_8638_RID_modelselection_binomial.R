setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testModelSelectionRIDBinomial <- function() {
  bhexFV <- h2o.importFile(locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
  numCols <- ncol(bhexFV)
  colNames <- names(bhexFV)
  Y <- "C21"
  totPredCols <- 20
  X <- c(1:totPredCols)
  bhexFV$C21 <- h2o.asfactor(bhexFV$C21)
  backwardModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, min_predictor_number=15, 
                                      mode="backward", influence="dfbetas", standardize=FALSE, family="binomial")
  backwardRID <- h2o.get_regression_influence_diagnostics(backwardModel)
  for (ind in seq(length(backwardRID))) {
    numPredictor <- length(backwardModel@model$coefficient_names[[ind]])-1
    expect_true((numPredictor*2+2) == h2o.ncol(backwardRID[[ind]]))
  }
}

doTest("ModelSelection with backward, maxr, maxrsweep: Binomial data and checking the regression influence diagnostics", testModelSelectionRIDBinomial)
