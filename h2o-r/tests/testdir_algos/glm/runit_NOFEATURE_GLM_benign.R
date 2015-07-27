setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

glm2Benign <- function(conn) { 
  # bhexFV <- h2o.importFile(conn, "./smalldata/logreg/benign.csv", destination_frame="benignFV.hex")
  bhexFV <- h2o.uploadFile(conn, locate("smalldata/logreg/benign.csv"), destination_frame="benignFV.hex")
  maxX <- 11
  Y <- 4
  X   <- 3:maxX
  X   <- X[ X != Y ] 
  
  Log.info("Build the model")
  mFV <- h2o.glm(y = Y, x = colnames(bhexFV)[X], training_frame = bhexFV, family = "binomial", nfolds = 5, alpha = 0, lambda = 1e-5)
  
  Log.info("Check that the columns used in the model are the ones we passed in.")
  
  Log.info("===================Columns passed in: ================")
  Log.info(paste("index ", X ," ", names(bhexFV)[X], "\n", sep=""))
  Log.info("======================================================")
  preds <- mFV@model$coefficients_table$Column
  preds <- preds[1:length(preds)-1]
  Log.info("===================Columns Used in Model: =========================")
  Log.info(paste(preds, "\n", sep=""))
  Log.info("================================================================")
  
  #Check coeffs here
  #tryCatch(expect_that(mFV@model$x, equals(colnames(bhexFV)[X])), error = function(e) Log.warn("Not getting colnames back, just indices"))
   expect_that(preds, equals(colnames(bhexFV)[X]))
  testEnd()
}

doTest("GLM: Benign Data", glm2Benign)

