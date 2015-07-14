setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.gbm.bin.accessors <- function() {
  Log.info("Making gbm with and without validation_frame...")
  pros.hex <- h2o.uploadFile(locate("smalldata/prostate/prostate.csv.zip"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")
  pros.gbm <- h2o.gbm(x = 3:9, y = 2, training_frame = pros.train)
  pros.gbm.valid <- h2o.gbm(x = 3:9, y = 2, training_frame = pros.train, validation_frame = pros.test)

  Log.info("MSE...")
  mse.basic <- h2o.mse(pros.gbm)
  print(mse.basic)
  expect_warning(h2o.mse(pros.gbm, valid = TRUE))
  mse.valid.F <- h2o.mse(pros.gbm.valid)
  mse.valid.T <- h2o.mse(pros.gbm.valid,valid = TRUE)
  print(mse.valid.T)
  print( paste0( "Expect Equal: ", mse.basic, " == ", mse.valid.F) )
  expect_true(mse.basic== mse.valid.F) # basic should equal valid with valid = FALSE

  Log.info("R^2...")
  r2.basic <- h2o.r2(pros.gbm)
  print(r2.basic)
  expect_warning(h2o.r2(pros.gbm, valid = TRUE))
  r2.valid.F <- h2o.r2(pros.gbm.valid)
  r2.valid.T <- h2o.r2(pros.gbm.valid,valid = TRUE)
  print(r2.valid.T)
  print( paste0( "Expect Equal: ", r2.basic, " == ", r2.valid.F) )
  expect_true(r2.basic==r2.valid.F) # basic should equal valid with valid = FALSE

  Log.info("LogLoss...")
  ll.basic <- h2o.logloss(pros.gbm)
  print(ll.basic)
  expect_warning(h2o.logloss(pros.gbm, valid = TRUE))
  ll.valid.F <- h2o.logloss(pros.gbm.valid)
  ll.valid.T <- h2o.logloss(pros.gbm.valid, valid = TRUE)
  print(ll.valid.T)
  print( paste0( "Expect Equal: ", ll.basic, " == ", ll.valid.F) )
  expect_equal(ll.basic, ll.valid.F) # basic should equal valid with valid = FALSE

  Log.info("AUC...")
  auc.basic <- h2o.auc(pros.gbm)
  print(auc.basic)
  expect_warning(h2o.auc(pros.gbm, valid = TRUE))
  auc.valid.F <- h2o.auc(pros.gbm.valid)
  auc.valid.T <- h2o.auc(pros.gbm.valid, valid = TRUE)
  print(auc.valid.T)
  print( paste0( "Expect Equal: ", auc.basic, " == ", auc.valid.F) )
  expect_equal(auc.basic, auc.valid.F) # basic should equal valid with valid = FALSE

  Log.info("Gini...")
  gini.basic <- h2o.giniCoef(pros.gbm)
  print(gini.basic)
  expect_warning(h2o.giniCoef(pros.gbm, valid = TRUE))
  gini.valid.F <- h2o.giniCoef(pros.gbm.valid)
  gini.valid.T <- h2o.giniCoef(pros.gbm.valid, valid = TRUE)
  print(gini.valid.T)
  print( paste0( "Expect Equal: ", gini.basic, " == ", gini.valid.F) )
  expect_equal(gini.basic, gini.valid.F) # basic should equal valid with valid = FALSE

  Log.info("Variable Importance...")
  print(h2o.varimp(pros.gbm))

  testEnd()
}

doTest("Testing model accessors for GBM", test.gbm.bin.accessors)
