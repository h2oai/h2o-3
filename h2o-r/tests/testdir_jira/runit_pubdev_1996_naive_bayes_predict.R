setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev_1996 <- function() {
  Log.info("Importing train and test data...")
  train <- h2o.uploadFile(locate("smalldata/jira/pub-1996_train.csv"), header = TRUE, col.types = rep("enum", 4))
  test <- h2o.uploadFile(locate("smalldata/jira/pub-1996_test.csv"), header = TRUE, col.types = rep("enum", 4))
  
  Log.info("Training data:"); print(summary(train))
  Log.info("Test data:"); print(summary(test))
  
  Log.info("Run Naive Bayes with x = c(1,2,3) and y = 4 on train")
  fitH2O <- h2o.naiveBayes(x = 1:3, y = 4, training_frame = train)
  print(fitH2O)
  
  Log.info("Predict on test data")
  pred <- predict(fitH2O, test)
  print(head(pred))
}

doTest("PUBDEV-1996: Naive Bayes prediction when test has different categoricals from train", test.pubdev_1996)
