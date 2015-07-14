setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.gbm.mult.accessors <- function() {
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"))
  i.sid <- h2o.runif(iris.hex)
  iris.train <- h2o.assign(iris.hex[i.sid > .2, ], "iris.train")
  iris.test <- h2o.assign(iris.hex[i.sid <= .2, ], "iris.test")
  iris.gbm <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.train)
  iris.gbm.valid <- h2o.gbm(x = 1:4, y = 5, training_frame = iris.train, validation_frame = iris.test)

  Log.info("MSE...")
  mse.basic <- h2o.mse(iris.gbm)
  print(mse.basic)
  expect_warning(h2o.mse(iris.gbm, valid = TRUE))
  mse.valid.F <- h2o.mse(iris.gbm.valid)
  mse.valid.T <- h2o.mse(iris.gbm.valid,valid = TRUE)
  print(mse.valid.T)
  expect_equal(mse.basic, mse.valid.F) # basic should equal valid with valid = FALSE

  Log.info("R^2...")
  r2.basic <- h2o.r2(iris.gbm)
  print(r2.basic)
  expect_warning(h2o.r2(iris.gbm, valid = TRUE))
  r2.valid.F <- h2o.r2(iris.gbm.valid)
  r2.valid.T <- h2o.r2(iris.gbm.valid,valid = TRUE)
  print(r2.valid.T)
  expect_equal(r2.basic, r2.valid.F) # basic should equal valid with valid = FALSE

  Log.info("LogLoss...")
  ll.basic <- h2o.logloss(iris.gbm)
  print(ll.basic)
  expect_warning(h2o.logloss(iris.gbm, valid = TRUE))
  ll.valid.F <- h2o.logloss(iris.gbm.valid)
  ll.valid.T <- h2o.logloss(iris.gbm.valid, valid = TRUE)
  print(ll.valid.T)
  expect_equal(ll.basic, ll.valid.F) # basic should equal valid with valid = FALSE

  Log.info("Hit Ratio...")
  hrt.basic <- h2o.hit_ratio_table(iris.gbm)
  print(hrt.basic)
  expect_warning(h2o.hit_ratio_table(iris.gbm, valid = TRUE))
  hrt.valid.F <- h2o.hit_ratio_table(iris.gbm.valid)
  hrt.valid.T <- h2o.hit_ratio_table(iris.gbm.valid,valid = TRUE)
  print(hrt.valid.T)
  expect_equal(hrt.basic, hrt.valid.F) # basic should equal valid with valid = FALSE

  Log.info("Variable Importance...")
  print(h2o.varimp(iris.gbm))

  testEnd()
}

doTest("Testing model accessors for GBM", test.gbm.mult.accessors)
