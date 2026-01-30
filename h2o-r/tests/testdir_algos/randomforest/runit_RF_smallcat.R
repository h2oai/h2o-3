setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


test.DRF.smallcat <- function() {
  # Training set has 26 categories from A to Z
  # Categories A, C, E, G, ... are perfect predictors of y = 1
  # Categories B, D, F, H, ... are perfect predictors of y = 0

  Log.info("Importing alphabet_cattest.csv data...\n")
  alphabet.hex <- h2o.uploadFile(locate("smalldata/gbm_test/alphabet_cattest.csv"), destination_frame = "alphabet.hex")
  alphabet.hex$y <- as.factor(alphabet.hex$y)
  Log.info("Summary of alphabet_cattest.csv from H2O:\n")
  print(summary(alphabet.hex))

  # Import CSV data for R to use in comparison
  alphabet.data <- read.csv(locate("smalldata/gbm_test/alphabet_cattest.csv"), header = TRUE)
  alphabet.data$y <- as.factor(alphabet.data$y)
  Log.info("Summary of alphabet_cattest.csv from R:\n")
  print(summary(alphabet.data))

  # Train H2O DRF Model:
  Log.info("H2O DRF (Group Split) with parameters:\nclassification = TRUE, ntree = 1, depth = 1, nbins = 100\n")
  drfmodel <- h2o.randomForest(x = "X", y = "y", training_frame = alphabet.hex,
                               ntrees = 1, max_depth = 1, min_rows = 100)
  print(drfmodel)

  # Check AUC and overall prediction error at least as good with group split than without
  Log.info("Expect DRF with Group Split to give Perfect Prediction in Single Iteration")
  drfperf <- h2o.performance(drfmodel)
  print(h2o.confusionMatrix(drfmodel,alphabet.hex))
  expect_equal(h2o.auc(drfperf), 1)
  # No errors off the diagonal
  default_cm <- h2o.confusionMatrix(drfmodel,alphabet.hex)
  expect_equal(default_cm[1,2], 0)
  expect_equal(default_cm[2,1], 0)
}

doTest("DRF Test: Classification with 26 categorical level predictor", test.DRF.smallcat)
