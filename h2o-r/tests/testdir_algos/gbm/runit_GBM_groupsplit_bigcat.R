setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


library(gbm)

test.GBM.bigcat <- function() {
  # Training set has 100 categories from cat001 to cat100
  # Categories cat001, cat003, ... are perfect predictors of y = 1
  # Categories cat002, cat004, ... are perfect predictors of y = 0
  
  h2oTest.logInfo("Importing bigcat_5000x2.csv data...\n")
  bigcat.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/bigcat_5000x2.csv"), destination_frame = "bigcat.hex")
  bigcat.hex$y <- as.factor(bigcat.hex$y)
  h2oTest.logInfo("Summary of bigcat_5000x2.csv from H2O:\n")
  print(summary(bigcat.hex))
  
  # Train H2O GBM Model:
  # No longer Naive, since group_split is always on by default
  h2oTest.logInfo("H2O GBM (Naive Split) with parameters:\nntrees = 1, max_depth = 1, nbins = 100\n")
  drfmodel.nogrp <- h2o.gbm(x = "X", y = "y", training_frame = bigcat.hex, ntrees = 1, max_depth = 1, nbins = 100, distribution = "bernoulli")
  print(drfmodel.nogrp)
  drfmodel.nogrp.perf <- h2o.performance(drfmodel.nogrp, bigcat.hex)
  
  h2oTest.logInfo("H2O GBM (Group Split) with parameters:\nntrees = 1, max_depth = 1, nbins = 100\n")
  drfmodel.grpsplit <- h2o.gbm(x = "X", y = "y", training_frame = bigcat.hex, ntrees = 1, max_depth = 1, nbins = 100, distribution = "bernoulli")
  print(drfmodel.grpsplit)
  drfmodel.grpsplit.perf <- h2o.performance(drfmodel.grpsplit, bigcat.hex)
  
  # Check AUC and overall prediction error at least as good with group split than without
  #expect_true(h2o.auc(drfmodel.grpsplit.perf) >= h2o.auc(drfmodel.nogrp.perf))
  #expect_true(h2o.accuracy(drfmodel.grpsplit.perf, 0.5) <= h2o.accuracy(drfmodel.nogrp.perf, 0.5))
  
}

h2oTest.doTest("GBM Test: Classification with 100 categorical level predictor", test.GBM.bigcat)
