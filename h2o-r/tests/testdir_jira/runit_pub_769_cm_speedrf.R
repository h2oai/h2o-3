setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################
# Test for PUB-769
# Ensure that the number of rows scored in the CM for binary classes is == number of rows in the dataset
######################################################################


options(echo=TRUE)


test.pub.767 <- function() {
  h2oTest.logInfo('Importing the altered prostatetype data from smalldata.')
  prostate <- h2o.importFile(normalizePath(h2oTest.locate('smalldata/logreg/prostate.csv')), 'prostate')

  h2oTest.logInfo('Print head of dataset')
  h2oTest.logInfo(head(prostate))
  prostate[,2] <- as.factor(prostate[,2]) # convert to Enum for classification

  m <- h2o.randomForest(x = 3:8, y = 2, training_frame = prostate, ntrees = 500,
                        max_depth = 100)

  h2oTest.logInfo("Number of rows in the confusion matrix for AUC:")
  p <- h2o.performance(m)
  print(h2o.confusionMatrix(p))

  print("Number of rows in the prostate dataset:")
  print(nrow(prostate))


  expect_equal(sum(h2o.confusionMatrix(m)[3,1:2]), nrow(prostate))
  
}

h2oTest.doTest("PUB-767: randomForest on discontinuous integer classes.", test.pub.767)
