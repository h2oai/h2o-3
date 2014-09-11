setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')
library(randomForest)

test.DRF.bigcat <- function(conn) {
  # Training set has 100 categories from cat001 to cat100
  # Categories cat001, cat003, ... are perfect predictors of y = 1
  # Categories cat002, cat004, ... are perfect predictors of y = 0
  
  Log.info("Importing bigcat_5000x2.csv data...\n")
  bigcat.hex <- h2o.uploadFile(conn, locate("smalldata/histogram_test/bigcat_5000x2.csv"), key = "bigcat.hex")
  bigcat.hex$y <- as.factor(bigcat.hex$y)
  Log.info("Summary of bigcat_5000x2.csv from H2O:\n")
  print(summary(bigcat.hex))
  
  # Train H2O DRF Model:
  Log.info("H2O DRF (Naive Split) with parameters:\nclassification = TRUE, ntree = 1, depth = 1, nbins = 100\n")
  drfmodel.nogrp <- h2o.randomForest(x = "X", y = "y", data = bigcat.hex, classification = TRUE, ntree = 1, depth = 1, nbins = 100, doGrpSplit = FALSE, type = "BigData")
  print(drfmodel.nogrp)
  
  Log.info("H2O DRF (Group Split) with parameters:\nclassification = TRUE, ntree = 1, depth = 1, nbins = 100\n")
  drfmodel.grpsplit <- h2o.randomForest(x = "X", y = "y", data = bigcat.hex, classification = TRUE, ntree = 1, depth = 1, nbins = 100, doGrpSplit = TRUE, type = "BigData")
  print(drfmodel.grpsplit)
  
  # Check AUC and overall prediction error at least as good with group split than without
  Log.info("Expect DRF with Group Split to give Perfect Prediction in Single Iteration")
  expect_true(drfmodel.grpsplit@model$auc == 1)
  expect_true(drfmodel.grpsplit@model$confusion[3,3] == 0)
  expect_true(drfmodel.grpsplit@model$auc >= drfmodel.nogrp@model$auc)
  expect_true(drfmodel.grpsplit@model$confusion[3,3] <= drfmodel.nogrp@model$confusion[3,3])
  testEnd()
}

doTest("DRF Test: Classification with 100 categorical level predictor", test.DRF.bigcat)
