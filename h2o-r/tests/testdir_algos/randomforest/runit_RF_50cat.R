setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.DRF.groupsplit <- function() {
  # Training set has only 45 categories cat1 through cat45
  h2oTest.logInfo("Importing 50_cattest_train.csv data...\n")
  train.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/50_cattest_train.csv"), destination_frame = "train.hex")
  train.hex$y <- as.factor(train.hex$y)
  h2oTest.logInfo("Summary of 50_cattest_train.csv from H2O:\n")
  print(summary(train.hex))

  # Train H2O DRF Model:
  h2oTest.logInfo(paste("H2O DRF with parameters:\nclassification = TRUE, ntree = 50, depth = 20, nbins = 500\n", sep = ""))
  drfmodel.h2o <- h2o.randomForest(x = c("x1", "x2"), y = "y",
                                   training_frame = train.hex, ntrees = 50,
                                   max_depth = 20, min_rows = 500)
  print(drfmodel.h2o)

  # Test dataset has all 50 categories cat1 through cat50
  h2oTest.logInfo("Importing 50_cattest_test.csv data...\n")
  test.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/50_cattest_test.csv"), destination_frame="test.hex")
  h2oTest.logInfo("Summary of 50_cattest_test.csv from H2O:\n")
  print(summary(test.hex))

  # Predict on test dataset with DRF model:
  h2oTest.logInfo("Performing predictions on test dataset...\n")
  drfmodel.pred <- predict(drfmodel.h2o, test.hex)
  # h2o.preds <- head(drfmodel.pred, nrow(drfmodel.pred))[,1]
  print(head(drfmodel.pred))

  # Get the confusion matrix and AUC
  h2oTest.logInfo("Confusion matrix of predictions (max accuracy):\n")
  # test.cm <- h2o.confusionMatrix(test.hex$y, drfmodel.pred[,1])
  # print(test.cm)
  test.perf <- h2o.performance(drfmodel.h2o, test.hex)
  print(test.perf)
  
}

h2oTest.doTest("DRF Test: Classification with 50 categorical level predictor", test.DRF.groupsplit)
