setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

testMaxrSweepDupPredictors <- function() {
  train <- h2o.importFile(locate("bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv"))
  Y <- "response"
  X <- h2o.colnames(train)
  X <- X[-201]
  # model without duplicated columns
  maxrsweepGLMModel <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = train, max_predictor_number=10, 
                                             mode="maxrsweep", build_glm_model=FALSE)
  coeffsMaxrsweepGLM <- h2o.coef(maxrsweepGLMModel)
  
  train2 <- h2o.importFile(locate("bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv"))
  train2 <- train2[-c(201)]
  train <- h2o.cbind(train2, train)
  Y <- "response"
  X <- h2o.colnames(train)
  X <- X[-401]
  maxrsweepGLMModelDup <- h2o.modelSelection(y=Y, x=X, seed=12345, training_frame = train, max_predictor_number=10, 
        mode="maxrsweep", build_glm_model=FALSE)
  coeffsMaxrsweepGLMDup <- h2o.coef(maxrsweepGLMModelDup)
  
  # make sure models with and without duplicated predictors yielding the same results
  numModels <- length(coeffsMaxrsweepGLM)
  expect_equal(numModels, length(coeffsMaxrsweepGLMDup))
  
  for (ind in c(1:numModels)) {
    oneCoeff <- coeffsMaxrsweepGLM[[ind]]
    oneCoeffNoDup <- coeffsMaxrsweepGLMDup[[ind]]
    expect_equal(oneCoeff, oneCoeff, tolerance=1e-6)
  }
}

doTest("ModelSelection with maxrsweep: make sure duplicate predictors are removed and no NPE will occur", testMaxrSweepDupPredictors)
