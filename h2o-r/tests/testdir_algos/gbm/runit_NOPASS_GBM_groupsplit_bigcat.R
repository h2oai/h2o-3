setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')
library(gbm)

test.GBM.bigcat <- function(conn) {
  # Training set has 100 categories from cat001 to cat100
  # Categories cat001, cat003, ... are perfect predictors of y = 1
  # Categories cat002, cat004, ... are perfect predictors of y = 0
  
  Log.info("Importing bigcat_5000x2.csv data...\n")
  bigcat.hex <- h2o.uploadFile(conn, locate("smalldata/histogram_test/bigcat_5000x2.csv"), key = "bigcat.hex")
  bigcat.hex$y <- as.factor(bigcat.hex$y)
  Log.info("Summary of bigcat_5000x2.csv from H2O:\n")
  print(summary(bigcat.hex))
  
  # Train H2O GBM Model:
  Log.info("H2O GBM (Naive Split) with parameters:\nntrees = 1, max_depth = 1, nbins = 100\n")
  drfmodel.nogrp <- h2o.gbm(x = "X", y = "y", training_frame = bigcat.hex, ntrees = 1, max_depth = 1, nbins = 100, group_split = FALSE)
  print(drfmodel.nogrp)
  
  Log.info("H2O GBM (Group Split) with parameters:\nntrees = 1, max_depth = 1, nbins = 100\n")
  drfmodel.grpsplit <- h2o.gbm(x = "X", y = "y", training_frame = bigcat.hex, ntrees = 1, max_depth = 1, nbins = 100, group_split = TRUE)
  print(drfmodel.grpsplit)
  
  # Check AUC and overall prediction error at least as good with group split than without
  expect_true(drfmodel.grpsplit@model$auc >= drfmodel.nogrp@model$auc)
  expect_true(drfmodel.grpsplit@model$confusion[3,3] <= drfmodel.nogrp@model$confusion[3,3])
  testEnd()
}

doTest("GBM Test: Classification with 100 categorical level predictor", test.GBM.bigcat)
