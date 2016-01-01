setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.groupsplit <- function() {
  # Training set has only 45 categories cat1 through cat45
  h2oTest.logInfo("Importing 50_cattest_train.csv data...\n")
  train.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/50_cattest_train.csv"), destination_frame = "train.hex")
  train.hex$y <- as.factor(train.hex$y)
  h2oTest.logInfo("Summary of 50_cattest_train.csv from H2O:\n")
  print(summary(train.hex))

  # Train H2O GBM Model:
  h2oTest.logInfo(paste("H2O GBM with parameters:\nntrees = 10, max_depth = 20, nbins = 20\n", sep = ""))
  drfmodel.h2o <- h2o.gbm(x = c("x1", "x2"), y = "y", training_frame = train.hex, ntrees = 10, max_depth = 5, nbins = 20, distribution = "bernoulli")
  print(drfmodel.h2o)

  # Test dataset has all 50 categories cat1 through cat50
  h2oTest.logInfo("Importing 50_cattest_test.csv data...\n")
  test.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/50_cattest_test.csv"), destination_frame="test.hex")
  h2oTest.logInfo("Summary of 50_cattest_test.csv from H2O:\n")
  print(summary(test.hex))

  # Predict on test dataset with GBM model:
  h2oTest.logInfo("Performing predictions on test dataset...\n")
  drfmodel.pred <- predict(drfmodel.h2o, test.hex)
  # h2o.preds <- head(drfmodel.pred, nrow(drfmodel.pred))[,1]
  print(head(drfmodel.pred))

  # Get the confusion matrix and AUC
  h2oTest.logInfo("Confusion matrix of predictions (max accuracy):\n")
  test.perf <- h2o.performance(drfmodel.h2o, test.hex)
  test.cm <- h2o.confusionMatrix(test.perf)
  test.auc <- h2o.auc(test.perf)
  
}

h2oTest.doTest("GBM Test: Classification with 50 categorical level predictor", test.GBM.groupsplit)
