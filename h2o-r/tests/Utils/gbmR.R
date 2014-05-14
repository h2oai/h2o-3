checkGBMModel <- function(myGBM.h2o, myGBM.r, h2oTest, RTest) {
  #Check GBM model against R
  #TODO: h2o.gbm ignore first tree (junk)
  myGBM.h2o@model$err <- myGBM.h2o@model$err[-1]
  Log.info("MSE by tree in H2O:")
  #print(myGBM.h2o@model$err)
  expect_true(length(myGBM.h2o@model$err) == n.trees) #ntrees is global
  Log.info("Gaussian Deviance by tree in R (i.e. the per tree 'train error'): \n")
  print(myGBM.r$train.error)
  #Log.info("Expect these to be close... mean of the absolute differences is < .5, and sd < 0.1")
  #errDiff <- abs(myGBM.r$train.error - myGBM.h2o@model$err)
  #Log.info(cat("Mean of the absolute difference is: ", mean(errDiff)))
  #Log.info(cat("Standard Deviation of the absolute difference is: ", sd(errDiff)))
  #expect_true(mean(errDiff) < 0.5)
  #expect_true(sd(errDiff) < 0.1)
 
  # Compare GBM models on out-of-sample data
  Log.info("Uploading GBM testing data...")
  ecologyTest.hex <- h2oTest
  ecologyTest.data <- RTest
  actual <- ecologyTest.data[,1]
  Log.info("Performing the predictions on h2o GBM model: ")

  # TODO: Building CM in R instead of in H2O
  h2ogbm.predict <- h2o.predict(myGBM.h2o, ecologyTest.hex)
  h2o.preds <- head(h2ogbm.predict,nrow(h2ogbm.predict))[,1]
  h2oCM <- table(actual,h2o.preds)
  Log.info("H2O CM is: \n")
  print(h2oCM)
  Log.info("Performing the predictions of R GBM model: ")
  R.preds <- ifelse(predict.gbm(myGBM.r, ecologyTest.data,n.trees=n.trees,type="response") < 0.5, 0,1)
  Log.info("R CM is: \n")
  RCM <- table(actual,R.preds)
  print(RCM)
  Log.info("Compare AUC from R and H2O:\n")
  Log.info("H2O AUC: ")
  print(gbm.roc.area(actual,h2o.preds))
  Log.info("R AUC: ")
  print(gbm.roc.area(actual,R.preds))
}