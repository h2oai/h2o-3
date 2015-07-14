setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.gbm.regr.accessors <- function() {
  cars.hex <- h2o.uploadFile(locate("smalldata/junit/cars.csv"))
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")
  cars.gbm <- h2o.gbm(x = 3:7, y = 2, training_frame = cars.train)
  cars.gbm.valid <- h2o.gbm(x = 3:7, y = 2, training_frame = cars.train, validation_frame = cars.test)

  Log.info("MSE...")
  mse.basic <- h2o.mse(cars.gbm)
  print(mse.basic)
  expect_warning(h2o.mse(cars.gbm, valid = TRUE))
  mse.valid.F <- h2o.mse(cars.gbm.valid)
  mse.valid.T <- h2o.mse(cars.gbm.valid,valid = TRUE)
  print(mse.valid.T)
  expect_equal(mse.basic, mse.valid.F) # basic should equal valid with valid = FALSE

  Log.info("R^2...")
  r2.basic <- h2o.r2(cars.gbm)
  print(r2.basic)
  expect_warning(h2o.r2(cars.gbm, valid = TRUE))
  r2.valid.F <- h2o.r2(cars.gbm.valid)
  r2.valid.T <- h2o.r2(cars.gbm.valid,valid = TRUE)
  print(r2.valid.T)
  expect_equal(r2.basic, r2.valid.F) # basic should equal valid with valid = FALSE

  Log.info("Variable Importance...")
  print(h2o.varimp(cars.gbm))

  testEnd()
}

doTest("Testing model accessors for GBM", test.gbm.regr.accessors)
