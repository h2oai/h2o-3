######################################################################
# Test for PUB-769
# Ensure that the number of rows scored in the CM for binary classes is == number of rows in the dataset
######################################################################

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
options(echo=TRUE)
source('../findNSourceUtils.R')

test.pub.767 <- function(conn) {
  Log.info('Importing the altered prostatetype data from smalldata.')
  prostate <- h2o.importFile(conn, normalizePath(locate('smalldata/logreg/prostate.csv')), 'prostate')
  
  Log.info('Print head of dataset')
  Log.info(head(prostate))

  m <- h2o.randomForest(x = 3:8, y = 2, data = prostate, ntree = 500, depth = 100) 

  Log.info("Number of rows in the confusion matrix for AUC:")
  print(sum(m@model$confusion[3,1:2]))
  
  print("Number of rows in the prostate dataset:")
  print(dim(prostate))
  

  expect_that(sum(m@model$confusion[3,1:2]), equals(dim(prostate)[1]))
  show(m)  
  testEnd()
}

doTest("PUB-767: randomForest on discontinuous integer classes.", test.pub.767)
