setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


library(randomForest)

test.DRF.bigcat <- function() {
  # Training set has 100 categories from cat001 to cat100
  # Categories cat001, cat003, ... are perfect predictors of y = 1
  # Categories cat002, cat004, ... are perfect predictors of y = 0

  h2oTest.logInfo("Importing bigcat_5000x2.csv data...\n")
  bigcat.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/bigcat_5000x2.csv"), destination_frame = "bigcat.hex")
  bigcat.hex$y <- as.factor(bigcat.hex$y)
  h2oTest.logInfo("Summary of bigcat_5000x2.csv from H2O:\n")
  print(summary(bigcat.hex))

  # Train H2O DRF Model:
  h2oTest.logInfo("H2O DRF (Group Split) with parameters:\nclassification = TRUE, ntree = 1, depth = 1, nbins = 100\n")
  drfmodel <- h2o.randomForest(x = "X", y = "y", training_frame = bigcat.hex,
                               ntrees = 1, max_depth = 1, min_rows = 100)
  print(drfmodel)

  # Check AUC and overall prediction error at least as good with group split than without
  h2oTest.logInfo("Expect DRF with Group Split to give Perfect Prediction in Single Iteration")
  drfperf <- h2o.performance(drfmodel)
  expect_equal(h2o.auc(drfperf), 1)
  # No errors off the diagonal
  default_cm <- h2o.confusionMatrix(drfmodel,bigcat.hex)[[1]]
#  expect_equal(default_cm[1,2], 0)
#  expect_equal(default_cm[2,1], 0)
  
}

h2oTest.doTest("DRF Test: Classification with 100 categorical level predictor", test.DRF.bigcat)
