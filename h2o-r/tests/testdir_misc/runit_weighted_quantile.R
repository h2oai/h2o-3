setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# This tests weighted quantile
# by comparing results with R's wtd.quntile function and sanity checking by ignoring rows with zero weight
# dataset - http://mlr.cs.umass.edu/ml/datasets/Bank+Marketing

test.wtd.quantile <- function(conn){
  a= h2o.importFile(h2oTest.locate("smalldata/gbm_test/bank-full.csv.zip"),destination_frame = "bank_UCI")
  dim(a)
  myX = 1:16
  myY = 17

  rowss =45211
  #Sample rows for 2-fold xval
  set.seed(123)
  ss = sample(1:rowss,size = 22000)
  ww = rep(1,rowss)
  ww[ss]=2

  #Parse fold column to h2o
  wei = as.h2o(ww,destination_frame = "weight")
  colnames(wei)

  #Cbind fold column to the original dataset
  a = h2o.assign(h2o.cbind(a,wei),key = "bank")
  dim(a)

  #Build gbm by specifying the fold column
  gg = h2o.gbm(x = myX,y = myY,training_frame = a,ntrees = 5,fold_column = "x",keep_cross_validation_predictions = T,model_id = "cv_gbm")

  #Define and use weights column
  ww[ss]=0
  wi = as.h2o(ww,destination_frame = "weight_col")

  #Predict
  pr = h2o.predict(gg,a)
  pred = as.data.frame(pr[,3])

  prframe = pr[,3]
  prframe$weights = wi

  # weighted h2o quantile
  hq = as.numeric(h2o.quantile(prframe,probs = seq(0,.95,.05),weights_column = "weights"))

  # weighted R quantile
  library(Hmisc)
  wq = as.numeric(wtd.quantile(pred[,1],ww,probs = seq(0,.95,.05)))
  #wq-hq
  expect_true(max(abs((wq-hq)/wq))<1e-3)

  #Sanity check with just nonzero weighted rows
  pp=pred[which(ww==1),]
  qq = as.numeric(quantile(pp,probs = seq(0,.95,.05)))
  #wq-qq
  expect_true(max(abs((wq-qq)/wq))< 1e-3)
  expect_true(max(abs((hq-qq)/wq))< 1e-3)
}
h2oTest.doTest("Test weighted quantile",test.wtd.quantile )

