setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# Run speedRf. Get Mean Squared error from the model.
# Predict on the same dataset and calculate Mean Squared error in R by pulling in the predictions


options(echo=TRUE)


test.pub.651 <- function() {
  print("Parsing the adult income dataset")
  adlt_income<-h2o.importFile(normalizePath(h2oTest.locate("smalldata/jira/adult.gz")),destination_frame="adlt_income")
  myX = 1:14
  myY = 15

  print("Building RF model")
  my.srf  = h2o.randomForest(x=myX,y=myY,training_frame=adlt_income,ntrees=50, validation_frame=adlt_income)
  print(paste(" The RF ran with this seed: ",my.srf@model$params$seed, sep = ''))
  print(my.srf)

  mse_from_model = h2o.mse(my.srf)
  print(paste("mean squared error from model page", mse_from_model, sep = ''))

  pred = h2o.predict(my.srf,adlt_income)
  print(pred)

  print("Pull prediction file into R")
  pred_toR = as.data.frame(pred)
  print("Pull dataset into R")
  ad_toR = as.data.frame(adlt_income)

  print("Calculate mean squared error in R from the probabilities in the prediction file")
  ss = pred_toR[which(pred_toR$predict==ad_toR$C15),] #------rows for which labels correctly predicted
  tt = pred_toR[which(pred_toR$predict!=ad_toR$C15),] #------rows for which labels not correctly predicted

  mse_calculatedfrom_predFile = mean(c((1-ss[which(ss[,1]=="<=50K"),2])^2, (1-ss[which(ss[,1]==">50K"),3])^2, (1-tt[which(tt[,1]==">50K"),2])^2, (1-tt[which(tt[,1]=="<=50K"),3])^2))

  print(paste("mean squared error from model page:  ", mse_from_model, sep = ''))
  print(paste("mean squared error as calculated from prediction file probabilities:  ", mse_calculatedfrom_predFile, sep = ' '))
  print("Expect the above two to be equal")
  expect_true( abs(mse_from_model - mse_calculatedfrom_predFile) < 1e-5 )
  
}

h2oTest.doTest("PUB-651: Test Predictions on SRF", test.pub.651)
