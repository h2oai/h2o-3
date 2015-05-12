setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.glm.bin.accessors <- function(conn) {
  cars.hex <- h2o.uploadFile(conn, locate("smalldata/junit/cars.csv"))
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")
  cars.glm <- h2o.glm(x = 3:7, y = 2, training_frame = cars.train)
  cars.glm.valid <- h2o.glm(x = 3:7, y = 2, training_frame = cars.train, validation_frame = cars.test)

  Log.info("MSE...")
  mse.basic <- h2o.mse(pros.glm)
  print(mse.basic)
  expect_warning(h2o.mse(pros.glm, valid = TRUE))
  mse.valid.F <- h2o.mse(pros.glm.valid)
  mse.valid.T <- h2o.mse(pros.glm.valid,valid = TRUE)
  print(mse.valid.T)
  expect_equal(mse.basic, mse.valid.F) # basic should equal valid with valid = FALSE
  expect_error(expect_equal(mse.basic, mse.valid.T))

  Log.info("R^2...")
  r2.basic <- h2o.r2(pros.glm)
  print(r2.basic)
  expect_warning(h2o.r2(pros.glm, valid = TRUE))
  r2.valid.F <- h2o.r2(pros.glm.valid)
  r2.valid.T <- h2o.r2(pros.glm.valid,valid = TRUE)
  print(r2.valid.T)
  expect_equal(r2.basic, r2.valid.F) # basic should equal valid with valid = FALSE
  expect_error(expect_equal(r2.basic, r2.valid.T))

  Log.info("LogLoss...")
  ll.basic <- h2o.logloss(pros.glm)
  print(ll.basic)
  expect_warning(h2o.logloss(pros.glm, valid = TRUE))
  ll.valid.F <- h2o.logloss(pros.glm.valid)
  ll.valid.T <- h2o.logloss(pros.glm.valid, valid = TRUE)
  print(ll.valid.T)
  expect_equal(ll.basic, ll.valid.F) # basic should equal valid with valid = FALSE
  expect_error(expect_equal(ll.basic, ll.valid.T))

  Log.info("Null Deviance...")
  nuldev.basic <- h2o.nullDev(pros.glm)
  print(nuldev.basic)
  expect_warning(h2o.nullDev(pros.glm, valid = TRUE))
  nuldev.valid.F <- h2o.nullDev(pros.glm.valid)
  nuldev.valid.T <- h2o.nullDev(pros.glm.valid, valid = TRUE)
  print(nuldev.valid.T)
  expect_equal(nuldev.basic, nuldev.valid.F) # basic should equal valid with valid = FALSE
  expect_error(expect_equal(nuldev.basic, nuldev.valid.T))

  Log.info("Residual Deviance...")
  resdev.basic <- h2o.resDev(pros.glm)
  print(resdev.basic)
  expect_warning(h2o.resDev(pros.glm, valid = TRUE))
  resdev.valid.F <- h2o.resDev(pros.glm.valid)
  resdev.valid.T <- h2o.resDev(pros.glm.valid, valid = TRUE)
  print(resdev.valid.T)
  expect_equal(resdev.basic, resdev.valid.F) # basic should equal valid with valid = FALSE
  expect_error(expect_equal(resdev.basic, resdev.valid.T))

  Log.info("AIC...")
  aic.basic <- h2o.aic(pros.glm)
  print(aic.basic)
  expect_warning(h2o.aic(pros.glm, valid = TRUE))
  aic.valid.F <- h2o.aic(pros.glm.valid)
  aic.valid.T <- h2o.aic(pros.glm.valid, valid = TRUE)
  print(aic.valid.T)
  expect_equal(aic.basic, aic.valid.F) # basic should equal valid with valid = FALSE
  expect_error(expect_equal(aic.basic, aic.valid.T))

  Log.info("Degrees of Freedom...")
  dof.basic <- h2o.dof(pros.glm)
  print(dof.basic)
  expect_warning(h2o.dof(pros.glm, valid = TRUE))
  dof.valid.F <- h2o.dof(pros.glm.valid)
  dof.valid.T <- h2o.dof(pros.glm.valid, valid = TRUE)
  print(dof.valid.T)
  expect_equal(dof.basic, dof.valid.F) # basic should equal valid with valid = FALSE
  expect_error(expect_equal(dof.basic, dof.valid.T))

  Log.info("Null Degrees of Freedom...")
  nulldof.basic <- h2o.nullDof(pros.glm)
  print(nulldof.basic)
  expect_warning(h2o.nullDof(pros.glm, valid = TRUE))
  nulldof.valid.F <- h2o.nullDof(pros.glm.valid)
  nulldof.valid.T <- h2o.nullDof(pros.glm.valid, valid = TRUE)
  print(nulldof.valid.T)
  expect_equal(nulldof.basic, nulldof.valid.F) # basic should equal valid with valid = FALSE
  expect_error(expect_equal(nulldof.basic, nulldof.valid.T))

  Log.info("Variable Importance...")
  print(h2o.varimp(pros.glm))

  testEnd()
}

doTest("Testing model accessors for GLM", test.glm.bin.accessors)
