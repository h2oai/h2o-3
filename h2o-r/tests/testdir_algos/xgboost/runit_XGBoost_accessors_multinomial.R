setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.xgboost.mult.accessors <- function() {
  expect_true(h2o.xgboost.available())

  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"))
  i.sid <- h2o.runif(iris.hex)
  iris.train <- h2o.assign(iris.hex[i.sid > .2, ], "iris.train")
  iris.test <- h2o.assign(iris.hex[i.sid <= .2, ], "iris.test")
  iris.xgboost <- h2o.xgboost(x = 1:4, y = 5, training_frame = iris.train)
  iris.xgboost.valid <- h2o.xgboost(x = 1:4, y = 5, training_frame = iris.train, validation_frame = iris.test)
  iris.xgboost.valid.xval <- h2o.xgboost(x = 1:4, y = 5, training_frame = iris.train, validation_frame = iris.test, nfolds=2)

  Log.info("MSE...")
  mse.basic <- h2o.mse(iris.xgboost)
  print(mse.basic)
  expect_warning(h2o.mse(iris.xgboost, valid = TRUE))
  mse.valid.F <- h2o.mse(iris.xgboost.valid)
  mse.valid.T <- h2o.mse(iris.xgboost.valid,valid = TRUE)
  print(mse.valid.T)
  expect_equal(mse.basic, mse.valid.F) # basic should equal valid with valid = FALSE
  mse.valid.xval.T <- h2o.mse(iris.xgboost.valid.xval,train=TRUE,valid=TRUE,xval=TRUE)
  expect_true(length(mse.valid.xval.T)==3)
  expect_true(mse.valid.xval.T["train"]==mse.basic)
  expect_true(mse.valid.xval.T["valid"]==mse.valid.T)

  Log.info("LogLoss...")
  ll.basic <- h2o.logloss(iris.xgboost)
  print(ll.basic)
  expect_warning(h2o.logloss(iris.xgboost, valid = TRUE))
  ll.valid.F <- h2o.logloss(iris.xgboost.valid)
  ll.valid.T <- h2o.logloss(iris.xgboost.valid, valid = TRUE)
  print(ll.valid.T)
  expect_equal(ll.basic, ll.valid.F) # basic should equal valid with valid = FALSE
  logloss.valid.xval.T <- h2o.logloss(iris.xgboost.valid.xval,train=TRUE,valid=TRUE,xval=TRUE)
  expect_true(length(logloss.valid.xval.T)==3)
  expect_true(logloss.valid.xval.T["train"]==ll.basic)
  expect_true(logloss.valid.xval.T["valid"]==ll.valid.T)

  Log.info("Hit Ratio...")
  hrt.basic <- h2o.hit_ratio_table(iris.xgboost)
  print(hrt.basic)
  expect_warning(h2o.hit_ratio_table(iris.xgboost, valid = TRUE))
  hrt.valid.F <- h2o.hit_ratio_table(iris.xgboost.valid)
  hrt.valid.T <- h2o.hit_ratio_table(iris.xgboost.valid,valid = TRUE)
  print(hrt.valid.T)
  expect_equal(hrt.basic, hrt.valid.F) # basic should equal valid with valid = FALSE
  hit_ratio_table.valid.xval.T <- h2o.hit_ratio_table(iris.xgboost.valid.xval,train=TRUE,valid=TRUE,xval=TRUE)
  expect_true(length(hit_ratio_table.valid.xval.T)==3)
  expect_equal(hit_ratio_table.valid.xval.T[["train"]],hrt.basic)
  expect_equal(hit_ratio_table.valid.xval.T[["valid"]],hrt.valid.T)

  Log.info("Variable Importance...")
  print(h2o.varimp(iris.xgboost))

  
}

doTest("Testing multinomial model accessors for XGBoost", test.xgboost.mult.accessors)
