setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev_1996 <- function() {
  h2oTest.logInfo("Importing train and test data...")
  train <- h2o.uploadFile(h2oTest.locate("smalldata/jira/pub-1996_train.csv"), header = TRUE, col.types = rep("enum", 4))
  test <- h2o.uploadFile(h2oTest.locate("smalldata/jira/pub-1996_test.csv"), header = TRUE, col.types = rep("enum", 4))
  
  h2oTest.logInfo("Training data:"); print(summary(train))
  h2oTest.logInfo("Test data:"); print(summary(test))
  
  h2oTest.logInfo("Run Naive Bayes with x = c(1,2,3) and y = 4 on train")
  fitH2O <- h2o.naiveBayes(x = 1:3, y = 4, training_frame = train)
  print(fitH2O)
  
  h2oTest.logInfo("Predict on test data")
  pred <- predict(fitH2O, test)
  print(head(pred))
}

h2oTest.doTest("PUBDEV-1996: Naive Bayes prediction when test has different categoricals from train", test.pubdev_1996)
