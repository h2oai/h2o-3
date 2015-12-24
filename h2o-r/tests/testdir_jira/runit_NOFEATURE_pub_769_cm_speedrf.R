setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pub.767 <- function() {
  Log.info('Importing the altered prostatetype data from smalldata.')
  prostate <- h2o.importFile( normalizePath(locate('smalldata/logreg/prostate.csv')), 'prostate')

  Log.info('Print head of dataset')
  Log.info(head(prostate))
  prostate[,2] <- as.factor(prostate[,2]) # convert to Enum for classification

  m <- h2o.randomForest(x = 3:8, y = 2, training_frame = prostate, ntrees = 500,
                        max_depth = 100)

  Log.info("Number of rows in the confusion matrix for AUC:")
  p <- h2o.performance(m)
  print(h2o.confusionMatrices(p, 0.1))

  print("Number of rows in the prostate dataset:")
  print(nrow(prostate))


  expect_equal(sum(m@model$confusion[3,1:2]), nrow(prostate))
}

doTest("PUB-767: randomForest on discontinuous integer classes.", test.pub.767)
