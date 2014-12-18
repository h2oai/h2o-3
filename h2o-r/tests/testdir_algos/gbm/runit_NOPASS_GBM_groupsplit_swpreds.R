setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.GBM.SWpreds <- function(conn) {
  # Training set has two predictor columns
  # X1: 10 categorical levels, 100 observations per level; X2: Unif(0,1) noise
  # Ratio of y = 1 per Level: cat01 = 1.0 (strong predictor), cat02 to cat10 = 0.5 (weak predictors)
  
  Log.info("Importing swpreds_1000x3.csv data...\n")
  swpreds.hex <- h2o.uploadFile(conn, locate("smalldata/gbm_test/swpreds_1000x3.csv"), key = "swpreds.hex")
  swpreds.hex[,3] <- as.factor(swpreds.hex[,3])
  Log.info("Summary of swpreds_1000x3.csv from H2O:\n")
  print(summary(swpreds.hex))
  
  # Train H2O GBM without Noise Column
  Log.info("Distributed Random Forest with only Predictor Column")
  Log.info("H2O GBM (Naive Split) with parameters:\nntrees = 50, max_depth = 20, nbins = 500\n")
  drfmodel.nogrp <- h2o.gbm(x = "X1", y = "y", training_frame = swpreds.hex, ntrees = 50, max_depth = 20, nbins = 500, group_split = FALSE)
  print(drfmodel.nogrp)
  
  Log.info("H2O GBM (Group Split) with parameters:\nntrees = 50, max_depth = 20, nbins = 500\n")
  drfmodel.grpsplit <- h2o.gbm(x = "X1", y = "y", training_frame = swpreds.hex, ntrees = 50, max_depth = 20, nbins = 500, group_split = TRUE)
  print(drfmodel.grpsplit)
  
  # Check AUC and overall prediction error at least as good with group split than without
  tol <- 1e-4     #  Note: Allow for certain tolerance
  expect_true(drfmodel.grpsplit@model$auc >= drfmodel.nogrp@model$auc - tol)
  expect_true(drfmodel.grpsplit@model$confusion[3,3] <= drfmodel.nogrp@model$confusion[3,3] + tol)
  
  # Train H2O GBM Model including Noise Column:
  Log.info("Distributed Random Forest including Noise Column")
  Log.info("H2O GBM (Naive Split) with parameters:\nntrees = 50, max_depth = 20, nbins = 500\n")
  drfmodel.nogrp2 <- h2o.gbm(x = c("X1", "X2"), y = "y", training_frame = swpreds.hex, ntrees = 50, max_depth = 20, nbins = 500, group_split = FALSE)
  print(drfmodel.nogrp2)
  
  Log.info("H2O GBM (Group Split) with parameters:\nntrees = 50, max_depth = 20, nbins = 500\n")
  drfmodel.grpsplit2 <- h2o.gbm(x = c("X1", "X2"), y = "y", training_frame = swpreds.hex, ntrees = 50, max_depth = 20, nbins = 500, group_split = TRUE)
  print(drfmodel.grpsplit2)
  
  # BUG? With noise, seems like AUC and/or prediction error can be slightly better with naive rather than group split
  #      This behavior is inconsistent over repeated runs when the seed is different
  # expect_true(drfmodel.grpsplit2@model$auc >= drfmodel.nogrp2@model$auc - tol)
  # expect_true(drfmodel.grpsplit2@model$confusion[3,3] <= drfmodel.nogrp2@model$confusion[3,3] + tol)
  
  testEnd()
}

doTest("GBM Test: Classification with Strong/Weak Predictors", test.GBM.SWpreds)
