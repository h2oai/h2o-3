setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.SWpreds <- function() {
  # Training set has two predictor columns
  # X1: 10 categorical levels, 100 observations per level; X2: Unif(0,1) noise
  # Ratio of y = 1 per Level: cat01 = 1.0 (strong predictor), cat02 to cat10 = 0.5 (weak predictors)
  
  h2oTest.logInfo("Importing swpreds_1000x3.csv data...\n")
  swpreds.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/swpreds_1000x3.csv"), destination_frame = "swpreds.hex")
  swpreds.hex[,3] <- as.factor(swpreds.hex[,3])
  h2oTest.logInfo("Summary of swpreds_1000x3.csv from H2O:\n")
  print(summary(swpreds.hex))
  
  # Train H2O GBM without Noise Column
  # No longer naive since group split is always on by default
  h2oTest.logInfo("Distributed Random Forest with only Predictor Column")
  h2oTest.logInfo("H2O GBM (Naive Split) with parameters:\nntrees = 50, max_depth = 20, nbins = 500\n")
  drfmodel.nogrp <- h2o.gbm(x = "X1", y = "y", training_frame = swpreds.hex, ntrees = 50, max_depth = 20, nbins = 500, distribution = "bernoulli")
  print(drfmodel.nogrp)
  drfmodel.nogrp.perf <- h2o.performance(drfmodel.nogrp, swpreds.hex)
  
  h2oTest.logInfo("H2O GBM (Group Split) with parameters:\nntrees = 50, max_depth = 20, nbins = 500\n")
  drfmodel.grpsplit <- h2o.gbm(x = "X1", y = "y", training_frame = swpreds.hex, ntrees = 50, max_depth = 20, nbins = 500, distribution = "bernoulli")
  print(drfmodel.grpsplit)
  drfmodel.grpsplit.perf <- h2o.performance(drfmodel.grpsplit, swpreds.hex)
  
  # Check AUC and overall prediction error at least as good with group split than without
  tol <- 1e-4     #  Note: Allow for certain tolerance
  #expect_true(h2o.auc(drfmodel.grpsplit.perf) >= h2o.auc(drfmodel.nogrp.perf) - tol)
  #expect_true(h2o.accuracy(drfmodel.grpsplit.perf, 0.5) <= h2o.accuracy(drfmodel.nogrp.perf, 0.5) + tol)

  # Train H2O GBM Model including Noise Column:
  h2oTest.logInfo("Distributed Random Forest including Noise Column")
  h2oTest.logInfo("H2O GBM (Naive Split) with parameters:\nntrees = 50, max_depth = 20, nbins = 500\n")
  drfmodel.nogrp2 <- h2o.gbm(x = c("X1", "X2"), y = "y", training_frame = swpreds.hex, ntrees = 50, max_depth = 20, nbins = 500, distribution = "bernoulli")
  print(drfmodel.nogrp2)
  drfmodel.nogrp2.perf <- h2o.performance(drfmodel.nogrp2, swpreds.hex)
  
  h2oTest.logInfo("H2O GBM (Group Split) with parameters:\nntrees = 50, max_depth = 20, nbins = 500\n")
  drfmodel.grpsplit2 <- h2o.gbm(x = c("X1", "X2"), y = "y", training_frame = swpreds.hex, ntrees = 50, max_depth = 20, nbins = 500, distribution = "bernoulli")
  print(drfmodel.grpsplit2)
  drfmodel.grpsplit2.perf <- h2o.performance(drfmodel.grpsplit2, swpreds.hex)
  
  # BUG? With noise, seems like AUC and/or prediction error can be slightly better with naive rather than group split
  #      This behavior is inconsistent over repeated runs when the seed is different
  #expect_true(h2o.auc(drfmodel.grpsplit2.perf) >= h2o.auc(drfmodel.nogrp2.perf) - tol)
  #expect_true(h2o.accuracy(drfmodel.grpsplit2.perf, 0.5) <= h2o.accuracy(drfmodel.nogrp2.perf, 0.5) + tol)
  
  
}

h2oTest.doTest("GBM Test: Classification with Strong/Weak Predictors", test.GBM.SWpreds)
