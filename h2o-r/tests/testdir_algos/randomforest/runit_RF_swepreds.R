setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.DRF.SWpreds <- function() {
  # Training set has two predictor columns
  # X1: 10 categorical levels, 100 observations per level; X2: Unif(0,1) noise
  # Ratio of y = 1 per Level: cat01 = 1.0 (strong predictor), cat02 to cat10 = 0.5 (weak predictors)

  h2oTest.logInfo("Importing swpreds_1000x3.csv data...\n")
  swpreds.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/swpreds_1000x3.csv"), destination_frame = "swpreds.hex")
  swpreds.hex[,3] <- as.factor(swpreds.hex[,3])
  h2oTest.logInfo("Summary of swpreds_1000x3.csv from H2O:\n")
  print(summary(swpreds.hex))

  # Train H2O DRF without Noise Column
  h2oTest.logInfo("Distributed Random Forest with only Predictor Column")
  h2oTest.logInfo("H2O DRF (Group Split) with parameters:\nclassification = TRUE, ntree = 50, depth = 20, nbins = 500\n")
  drfmodel <- h2o.randomForest(x = "X1", y = "y", training_frame = swpreds.hex,
                               ntrees = 50, max_depth = 20, min_rows = 500)
  print(drfmodel)

  # Train H2O DRF Model including Noise Column:
  h2oTest.logInfo("Distributed Random Forest including Noise Column")
  h2oTest.logInfo("H2O DRF (Group Split) with parameters:\nclassification = TRUE, ntree = 50, depth = 20, nbins = 500\n")
  drfmodel <- h2o.randomForest(x = c("X1", "X2"), y = "y", training_frame = swpreds.hex,
                               ntrees = 50, max_depth = 20, min_rows = 500)
  print(drfmodel)

  # BUG? With noise, seems like AUC and/or prediction error can be slightly better with naive rather than group split
  #      This behavior is inconsistent over repeated runs when the seed is different
  # expect_true(drfmodel.grpsplit2@model$AUC >= drfmodel.nogrp2@model$AUC - tol)
  # expect_true(drfmodel.grpsplit2@model$confusion[3,3] <= drfmodel.nogrp2@model$confusion[3,3] + tol)

  
}

h2oTest.doTest("DRF Test: Classification with Strong/Weak Predictors", test.DRF.SWpreds)
