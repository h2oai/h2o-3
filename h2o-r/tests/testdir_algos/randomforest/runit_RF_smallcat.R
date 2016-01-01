setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")


library(randomForest)

test.DRF.smallcat <- function() {
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

  # Train H2O DRF Model:
  h2oTest.logInfo("H2O DRF (Group Split) with parameters:\nclassification = TRUE, ntree = 1, depth = 1, nbins = 100\n")
  drfmodel <- h2o.randomForest(x = "X", y = "y", training_frame = alphabet.hex,
                               ntrees = 1, max_depth = 1, min_rows = 100)
  print(drfmodel)

  # Check AUC and overall prediction error at least as good with group split than without
  h2oTest.logInfo("Expect DRF with Group Split to give Perfect Prediction in Single Iteration")
  drfperf <- h2o.performance(drfmodel)
  print(h2o.confusionMatrix(drfmodel,alphabet.hex))
  expect_equal(h2o.auc(drfperf), 1)
  # No errors off the diagonal
  default_cm <- h2o.confusionMatrix(drfmodel,alphabet.hex)[[1]]
  #iexpect_equal(default_cm[1,2], 0)
  #expect_equal(default_cm[2,1], 0)

  # Train R DRF Model:
  # h2oTest.logInfo("R DRF with same parameters:")
  # drfmodel.r <- randomForest(y ~ ., data = alphabet.data, ntree = 1, nodesize = 1)
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

h2oTest.doTest("DRF Test: Classification with 26 categorical level predictor", test.DRF.smallcat)
