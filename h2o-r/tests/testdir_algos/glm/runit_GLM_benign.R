setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



glm2Benign <- function() {

  bhexFV <- h2o.uploadFile(h2oTest.locate("smalldata/logreg/benign.csv"), destination_frame="benignFV.hex")
  maxX <- 11
  Y <- 4
  X   <- 3:maxX
  X   <- X[ X != Y ] 
  
  h2oTest.logInfo("Build the model")
  mFV <- h2o.glm(y = Y, x = colnames(bhexFV)[X], training_frame = bhexFV, family = "binomial", nfolds = 5, alpha = 0, lambda = 1e-5)
  
  h2oTest.logInfo("Check that the columns used in the model are the ones we passed in.")
  
  h2oTest.logInfo("===================Columns passed in: ================")
  h2oTest.logInfo(paste("index ", X ," ", names(bhexFV)[X], "\n", sep=""))
  h2oTest.logInfo("======================================================")
  preds <- mFV@model$coefficients_table$names
  preds <- preds[2:length(preds)]
  h2oTest.logInfo("===================Columns Used in Model: =========================")
  h2oTest.logInfo(paste(preds, "\n", sep=""))
  h2oTest.logInfo("================================================================")

  expect_that(preds, equals(colnames(bhexFV)[X]))
  
}

h2oTest.doTest("GLM: Benign Data", glm2Benign)

