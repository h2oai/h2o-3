setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.glm.bin.accessors <- function() {
  cars.hex <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars.csv"))
  cars.hex[,3] <- as.factor(cars.hex[,3])
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")
  cars.glm <- h2o.glm(x = 3:7, y = 2, training_frame = cars.train)
  cars.glm.valid <- h2o.glm(x = 3:7, y = 2, training_frame = cars.train, validation_frame = cars.test)

  h2oTest.logInfo("MSE...")
  mse.basic <- h2o.mse(cars.glm)
  print(mse.basic)
  expect_warning(h2o.mse(cars.glm, valid = TRUE))
  mse.valid.F <- h2o.mse(cars.glm.valid)
  mse.valid.T <- h2o.mse(cars.glm.valid,valid = TRUE)
  print(mse.valid.T)
  expect_equal(mse.basic, mse.valid.F) # basic should equal valid with valid = FALSE
  expect_true(mse.basic != mse.valid.T)

  h2oTest.logInfo("R^2...")
  r2.basic <- h2o.r2(cars.glm)
  print(r2.basic)
  expect_warning(h2o.r2(cars.glm, valid = TRUE))
  r2.valid.F <- h2o.r2(cars.glm.valid)
  r2.valid.T <- h2o.r2(cars.glm.valid,valid = TRUE)
  print(r2.valid.T)
  expect_equal(r2.basic, r2.valid.F) # basic should equal valid with valid = FALSE
  expect_true(r2.basic != r2.valid.T)

#  h2oTest.logInfo("LogLoss...")
#  ll.basic <- h2o.logloss(cars.glm)
#  print(ll.basic)
#  expect_warning(h2o.logloss(cars.glm, valid = TRUE))
#  ll.valid.F <- h2o.logloss(cars.glm.valid)
#  ll.valid.T <- h2o.logloss(cars.glm.valid, valid = TRUE)
#  print(ll.valid.T)
#  expect_equal(ll.basic, ll.valid.F) # basic should equal valid with valid = FALSE
#  expect_true(ll.basic != ll.valid.T)

  h2oTest.logInfo("Null Deviance...")
  nuldev.basic <- h2o.null_deviance(cars.glm)
  print(nuldev.basic)
  expect_warning(h2o.null_deviance(cars.glm, valid = TRUE))
  nuldev.valid.F <- h2o.null_deviance(cars.glm.valid)
  nuldev.valid.T <- h2o.null_deviance(cars.glm.valid, valid = TRUE)
  print(nuldev.valid.T)
  expect_equal(nuldev.basic, nuldev.valid.F) # basic should equal valid with valid = FALSE
  expect_true(nuldev.basic != nuldev.valid.T)

  h2oTest.logInfo("Residual Deviance...")
  resdev.basic <- h2o.residual_deviance(cars.glm)
  print(resdev.basic)
  expect_warning(h2o.residual_deviance(cars.glm, valid = TRUE))
  resdev.valid.F <- h2o.residual_deviance(cars.glm.valid)
  resdev.valid.T <- h2o.residual_deviance(cars.glm.valid, valid = TRUE)
  print(resdev.valid.T)
  expect_equal(resdev.basic, resdev.valid.F) # basic should equal valid with valid = FALSE
  expect_true(resdev.basic != resdev.valid.T)

  h2oTest.logInfo("AIC...")
  aic.basic <- h2o.aic(cars.glm)
  print(aic.basic)
  expect_warning(h2o.aic(cars.glm, valid = TRUE))
  aic.valid.F <- h2o.aic(cars.glm.valid)
  aic.valid.T <- h2o.aic(cars.glm.valid, valid = TRUE)
  print(aic.valid.T)
  expect_equal(aic.basic, aic.valid.F) # basic should equal valid with valid = FALSE
  expect_true(aic.basic != aic.valid.T)

  h2oTest.logInfo("Degrees of Freedom...")
  dof.basic <- h2o.residual_dof(cars.glm)
  print(dof.basic)
  expect_warning(h2o.residual_dof(cars.glm, valid = TRUE))
  dof.valid.F <- h2o.residual_dof(cars.glm.valid)
  dof.valid.T <- h2o.residual_dof(cars.glm.valid, valid = TRUE)
  print(dof.valid.T)
  expect_equal(dof.basic, dof.valid.F) # basic should equal valid with valid = FALSE
  expect_true(dof.basic != dof.valid.T)

  h2oTest.logInfo("Null Degrees of Freedom...")
  nulldof.basic <- h2o.null_dof(cars.glm)
  print(nulldof.basic)
  expect_warning(h2o.null_dof(cars.glm, valid = TRUE))
  nulldof.valid.F <- h2o.null_dof(cars.glm.valid)
  nulldof.valid.T <- h2o.null_dof(cars.glm.valid, valid = TRUE)
  print(nulldof.valid.T)
  expect_equal(nulldof.basic, nulldof.valid.F) # basic should equal valid with valid = FALSE
  expect_true(nulldof.basic != nulldof.valid.T)

  h2oTest.logInfo("Variable Importance...")
  print(h2o.varimp(cars.glm))

  
}

h2oTest.doTest("Testing model accessors for GLM", test.glm.bin.accessors)
