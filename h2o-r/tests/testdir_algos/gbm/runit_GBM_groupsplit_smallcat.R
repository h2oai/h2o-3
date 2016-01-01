setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


library(gbm)

test.GBM.smallcat <- function() {
  # Training set has 26 categories from A to Z
  # Categories A, C, E, G, ... are perfect predictors of y = 1
  # Categories B, D, F, H, ... are perfect predictors of y = 0
  
  h2oTest.logInfo("Importing alphabet_cattest.csv data...\n")
  alphabet.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/alphabet_cattest.csv"), destination_frame = "alphabet.hex")
  alphabet.hex$y <- as.factor(alphabet.hex$y)
  h2oTest.logInfo("Summary of alphabet_cattest.csv from H2O:\n")
  print(summary(alphabet.hex))
  
  # Import CSV data for R to use in comparison
  alphabet.data <- read.csv(h2oTest.locate("smalldata/gbm_test/alphabet_cattest.csv"), header = TRUE)
  alphabet.data$y <- as.factor(alphabet.data$y)
  h2oTest.logInfo("Summary of alphabet_cattest.csv from R:\n")
  print(summary(alphabet.data))
  
  # Train H2O GBM Model:
  # No longer naive since group_split is always on
  h2oTest.logInfo("H2O GBM (Naive Split) with parameters:\nntrees = 1, max_depth = 1, nbins = 100\n")
  drfmodel.nogrp <- h2o.gbm(x = "X", y = "y", training_frame = alphabet.hex, ntrees = 1, max_depth = 1, nbins = 100, distribution = "bernoulli")
  print(drfmodel.nogrp)
  drfmodel.nogrp.perf <- h2o.performance(drfmodel.nogrp, alphabet.hex)
  
  h2oTest.logInfo("H2O GBM (Group Split) with parameters:\nntrees = 1, max_depth = 1, nbins = 100\n")
  drfmodel.grpsplit <- h2o.gbm(x = "X", y = "y", training_frame = alphabet.hex, ntrees = 1, max_depth = 1, nbins = 100, distribution = "bernoulli")
  print(drfmodel.grpsplit)
  drfmodel.grpsplit.perf <- h2o.performance(drfmodel.grpsplit, alphabet.hex)
  
  # Check AUC and overall prediction error at least as good with group split than without
  #h2oTest.logInfo("Expect GBM with Group Split to give Perfect Prediction in Single Iteration")
  #expect_true(drfmodel.grpsplit@model$AUC == 1)
  #expect_true(drfmodel.grpsplit@model$confusion[3,3] == 0)
  #expect_true(h2o.auc(drfmodel.grpsplit.perf) >= h2o.auc(drfmodel.nogrp.perf))
  #expect_true(h2o.accuracy(drfmodel.grpsplit.perf, 0.5) <= h2o.accuracy(drfmodel.nogrp.perf, 0.5))
  
  # Train R GBM Model:
  # h2oTest.logInfo("R GBM with same parameters:")
  # drfmodel.r <- gbm(y ~ ., data = alphabet.data, n.trees = 1, nodesize = 1)
  # drfmodel.r.pred <- predict(drfmodel.r, alphabet.data, type = "response")
  
  # Compute confusion matrices
  # h2oTest.logInfo("R Confusion Matrix:"); print(drfmodel.r$confusion)
  # h2oTest.logInfo("H2O (Group Split) Confusion Matrix:"); print(drfmodel.grpsplit@model$confusion)
  
  # Compute the AUC - need to convert factors back to numeric
  # actual <- ifelse(alphabet.data$y == "0", 0, 1)
  # pred <- ifelse(drfmodel.r.pred == "0", 0, 1)
  # R.auc = gbm.roc.area(actual, pred)
  # h2oTest.logInfo(paste("R AUC:", R.auc, "\tH2O (Group Split) AUC:", drfmodel.grpsplit@model$AUC))
  
}

h2oTest.doTest("GBM Test: Classification with 26 categorical level predictor", test.GBM.smallcat)
