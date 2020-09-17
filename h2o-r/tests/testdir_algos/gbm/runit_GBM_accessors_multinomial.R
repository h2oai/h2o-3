setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.gbm.mult.accessors <- function() {
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"))
  i.sid <- h2o.runif(iris.hex)
  iris.train <- h2o.assign(iris.hex[i.sid > .2, ], "iris.train")
  iris.test <- h2o.assign(iris.hex[i.sid <= .2, ], "iris.test")
  iris.gbm <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.train)
  iris.gbm.valid <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.train, validation_frame = iris.test)
  iris.gbm.valid.xval <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.train, validation_frame = iris.test, nfolds=2)

  Log.info("MSE...")
  mse.basic <- h2o.mse(iris.gbm)
  print(mse.basic)
  expect_warning(h2o.mse(iris.gbm, valid = TRUE))
  mse.valid.F <- h2o.mse(iris.gbm.valid)
  mse.valid.T <- h2o.mse(iris.gbm.valid,valid = TRUE)
  print(mse.valid.T)
  expect_equal(mse.basic, mse.valid.F) # basic should equal valid with valid = FALSE
  mse.valid.xval.T <- h2o.mse(iris.gbm.valid.xval,train=TRUE,valid=TRUE,xval=TRUE)
  expect_true(length(mse.valid.xval.T)==3)
  expect_true(mse.valid.xval.T["train"]==mse.basic)
  expect_true(mse.valid.xval.T["valid"]==mse.valid.T)

  Log.info("LogLoss...")
  ll.basic <- h2o.logloss(iris.gbm)
  print(ll.basic)
  expect_warning(h2o.logloss(iris.gbm, valid = TRUE))
  ll.valid.F <- h2o.logloss(iris.gbm.valid)
  ll.valid.T <- h2o.logloss(iris.gbm.valid, valid = TRUE)
  print(ll.valid.T)
  expect_equal(ll.basic, ll.valid.F) # basic should equal valid with valid = FALSE
  logloss.valid.xval.T <- h2o.logloss(iris.gbm.valid.xval,train=TRUE,valid=TRUE,xval=TRUE)
  expect_true(length(logloss.valid.xval.T)==3)
  expect_true(logloss.valid.xval.T["train"]==ll.basic)
  expect_true(logloss.valid.xval.T["valid"]==ll.valid.T)

  Log.info("Hit Ratio...")
  hrt.basic <- h2o.hit_ratio_table(iris.gbm)
  print(hrt.basic)
  expect_warning(h2o.hit_ratio_table(iris.gbm, valid = TRUE))
  hrt.valid.F <- h2o.hit_ratio_table(iris.gbm.valid)
  hrt.valid.T <- h2o.hit_ratio_table(iris.gbm.valid,valid = TRUE)
  print(hrt.valid.T)
  expect_equal(hrt.basic, hrt.valid.F) # basic should equal valid with valid = FALSE
  hit_ratio_table.valid.xval.T <- h2o.hit_ratio_table(iris.gbm.valid.xval,train=TRUE,valid=TRUE,xval=TRUE)
  expect_true(length(hit_ratio_table.valid.xval.T)==3)
  expect_equal(hit_ratio_table.valid.xval.T[["train"]],hrt.basic)
  expect_equal(hit_ratio_table.valid.xval.T[["valid"]],hrt.valid.T)

  Log.info("Variable Importance...")
  print(h2o.varimp(iris.gbm))

  Log.info("Multinomial AUC")
  auc1 <- iris.gbm@model$training_metrics@metrics$multinomial_auc
  auc2 <- h2o.multinomial_auc(iris.gbm@model$training_metrics)
  print(auc1)
  print(auc2)
  expect_equal(auc1, auc2)
  
  Log.info("Multinomial PR AUC")  
  aucpr1 <- iris.gbm@model$training_metrics@metrics$multinomial_pr_auc
  aucpr2 <- h2o.multinomial_aucpr(iris.gbm@model$training_metrics)  
  print(aucpr1)
  print(aucpr2)
  expect_equal(aucpr1, aucpr2)  
}

doTest("Testing model accessors for GBM", test.gbm.mult.accessors)
