setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.xgboost.bin.accessors <- function() {
  expect_true(h2o.xgboost.available())

  Log.info("Making xgboost with and without validation_frame...")
  pros.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv.zip"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
#  pros.hex[,4] <- as.factor(pros.hex[,4])
#  pros.hex[,5] <- as.factor(pros.hex[,5])
#  pros.hex[,6] <- as.factor(pros.hex[,6])
#  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex, seed=1234)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")
  pros.xgboost <- h2o.xgboost(x = 3:9, y = 2, training_frame = pros.train)
  pros.xgboost.valid <- h2o.xgboost(x = 3:9, y = 2, training_frame = pros.train, validation_frame = pros.test)
  pros.xgboost.valid.xval <- h2o.xgboost(x = 3:9, y = 2, training_frame = pros.train, validation_frame = pros.test, nfolds=2)

  Log.info("MSE...")
  mse.basic <- h2o.mse(pros.xgboost)
  print(mse.basic)
  expect_warning(h2o.mse(pros.xgboost, valid = TRUE))
  mse.valid.F <- h2o.mse(pros.xgboost.valid)
  mse.valid.T <- h2o.mse(pros.xgboost.valid,valid = TRUE)
  print(mse.valid.T)
  print( paste0( "Expect Equal: ", mse.basic, " == ", mse.valid.F) )
  expect_true(mse.basic== mse.valid.F) # basic should equal valid with valid = FALSE
  mse.valid.xval.T <- h2o.mse(pros.xgboost.valid.xval,train=TRUE,valid=TRUE,xval=TRUE)
  expect_true(length(mse.valid.xval.T)==3)
  expect_true(mse.valid.xval.T["train"]==mse.basic)
  expect_true(mse.valid.xval.T["valid"]==mse.valid.T)

  Log.info("LogLoss...")
  ll.basic <- h2o.logloss(pros.xgboost)
  print(ll.basic)
  expect_warning(h2o.logloss(pros.xgboost, valid = TRUE))
  ll.valid.F <- h2o.logloss(pros.xgboost.valid)
  ll.valid.T <- h2o.logloss(pros.xgboost.valid, valid = TRUE)
  print(ll.valid.T)
  print( paste0( "Expect Equal: ", ll.basic, " == ", ll.valid.F) )
  expect_equal(ll.basic, ll.valid.F) # basic should equal valid with valid = FALSE
  logloss.valid.xval.T <- h2o.logloss(pros.xgboost.valid.xval,train=TRUE,valid=TRUE,xval=TRUE)
  expect_true(length(logloss.valid.xval.T)==3)
  expect_true(logloss.valid.xval.T["train"]==ll.basic)
  expect_true(logloss.valid.xval.T["valid"]==ll.valid.T)

  Log.info("AUC...")
  auc.basic <- h2o.auc(pros.xgboost)
  print(auc.basic)
  expect_warning(h2o.auc(pros.xgboost, valid = TRUE))
  auc.valid.F <- h2o.auc(pros.xgboost.valid)
  auc.valid.T <- h2o.auc(pros.xgboost.valid, valid = TRUE)
  print(auc.valid.T)
  print( paste0( "Expect Equal: ", auc.basic, " == ", auc.valid.F) )
  expect_equal(auc.basic, auc.valid.F) # basic should equal valid with valid = FALSE
  auc.valid.xval.T <- h2o.auc(pros.xgboost.valid.xval,train=TRUE,valid=TRUE,xval=TRUE)
  expect_true(length(auc.valid.xval.T)==3)
  expect_true(auc.valid.xval.T["train"]==auc.basic)
  expect_true(auc.valid.xval.T["valid"]==auc.valid.T)

  Log.info("Gini...")
  gini.basic <- h2o.giniCoef(pros.xgboost)
  print(gini.basic)
  expect_warning(h2o.giniCoef(pros.xgboost, valid = TRUE))
  gini.valid.F <- h2o.giniCoef(pros.xgboost.valid)
  gini.valid.T <- h2o.giniCoef(pros.xgboost.valid, valid = TRUE)
  print(gini.valid.T)
  print( paste0( "Expect Equal: ", gini.basic, " == ", gini.valid.F) )
  expect_equal(gini.basic, gini.valid.F) # basic should equal valid with valid = FALSE
  giniCoef.valid.xval.T <- h2o.giniCoef(pros.xgboost.valid.xval,train=TRUE,valid=TRUE,xval=TRUE)
  expect_true(length(giniCoef.valid.xval.T)==3)
  expect_true(giniCoef.valid.xval.T["train"]==gini.basic)
  expect_true(giniCoef.valid.xval.T["valid"]==gini.valid.T)

  Log.info("Variable Importance...")
  print(h2o.varimp(pros.xgboost))

  
}

doTest("Testing binomial model accessors for XGBoost", test.xgboost.bin.accessors)
