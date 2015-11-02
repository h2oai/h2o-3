


glm2Benign <- function() {

  bhexFV <- h2o.uploadFile(locate("smalldata/logreg/benign.csv"), destination_frame="benignFV.hex")
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
  preds <- mFV@model$coefficients_table$names
  preds <- preds[2:length(preds)]
  Log.info("===================Columns Used in Model: =========================")
  Log.info(paste(preds, "\n", sep=""))
  Log.info("================================================================")

  expect_that(preds, equals(colnames(bhexFV)[X]))
  
}

doTest("GLM: Benign Data", glm2Benign)

