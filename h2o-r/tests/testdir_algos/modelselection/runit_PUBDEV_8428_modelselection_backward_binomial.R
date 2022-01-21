setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testModelSelection <- function() {
  bhexFV <- h2o.importFile(locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
  numCols <- ncol(bhexFV)
  colNames <- names(bhexFV)
  Y <- numCols
  totPredCols <- Y-1
  X <- c(1:totPredCols)
  backwardModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = bhexFV, min_predictor_number=10 , mode="backward", family='binomial', 
                                      link='logit')
  resultsF <- h2o.result(backwardModel) # check coefficients length are valid
  numModels <- h2o.nrow(resultsF)
  allCoefs <- backwardModel@model$best_model_predictors

  for (ind in seq(numModels, 2, -1)) {
    predNamesL <- allCoefs[[ind]]
    predNamesS <- allCoefs[[ind-1]]
    predMissing <- xor(predNamesL, predNamesS)
    print(predMissing)
    coefs <- h2o.coef(backwardModel, length(predNamesL))
    expect_equal(length(coefs), length(predNamesL)+1, tol=1e-6) # coefficient length should be equal to arguments used
  }
}

xor <- function(large, small) {
  for (ele in large)
    if (!(ele %in% small ))
      return(ele)
}

doTest("ModelSelection with backward: binomial data", testModelSelection)
