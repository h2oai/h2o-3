# Run speedRf. Get Mean Squared error from the model.
# Predict on the same dataset and calculate Mean Squared error in R by pulling in the predictions

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

test.pub.651 <- function(conn) {
  print("Parsing the adult income dataset")
  adlt_income<-h2o.importFile(normalizePath(locate("smalldata/jira/adult.gz")),destination_frame="adlt_income")
  myX = 1:14
  myY = 15

  print("Building SpeedRF model")
  my.srf  = h2o.randomForest(x=myX,y=myY,training_frame=adlt_income,ntrees=50,
                             oobee=F,validation=adlt_income)
  print(paste(" The SpeedRF ran with this seed: ",my.srf@model$params$seed, sep = ''))
  print(my.srf)

  mse_from_model = my.srf@model$mse
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
  testEnd()
}

doTest("PUB-651: Test Predictions on SRF", test.pub.651)
