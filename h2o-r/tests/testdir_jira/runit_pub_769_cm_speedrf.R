######################################################################
# Test for PUB-769
# Ensure that the number of rows scored in the CM for binary classes is == number of rows in the dataset
######################################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../h2o-runit.R')

test.pub.767 <- function() {
  Log.info('Importing the altered prostatetype data from smalldata.')
  prostate <- h2o.importFile(normalizePath(locate('smalldata/logreg/prostate.csv')), 'prostate')

  Log.info('Print head of dataset')
  Log.info(head(prostate))
  prostate[,2] <- as.factor(prostate[,2]) # convert to Enum for classification

  m <- h2o.randomForest(x = 3:8, y = 2, training_frame = prostate, ntrees = 500,
                        max_depth = 100)

  Log.info("Number of rows in the confusion matrix for AUC:")
  p <- h2o.performance(m)
  print(h2o.confusionMatrix(p))

  print("Number of rows in the prostate dataset:")
  print(nrow(prostate))


  expect_equal(sum(h2o.confusionMatrix(m)[3,1:2]), nrow(prostate))
  
}

doTest("PUB-767: randomForest on discontinuous integer classes.", test.pub.767)
