setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.nbayes.init_err <- function() {
  h2oTest.logInfo("Importing iris_wheader.csv data...\n")
  iris.hex <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris_wheader.csv"))
  iris.sum <- summary(iris.hex)
  print(iris.sum)
  
  h2oTest.logInfo("Laplace smoothing parameter is negative")
  expect_error(h2o.naiveBayes(x = 1:4, y = 5, training_frame = iris.hex, laplace = -1))
  
  h2oTest.logInfo("Minimum standard deviation is zero")
  expect_error(h2o.naiveBayes(x = 1:4, y = 5, training_frame = iris.hex, min_sdev = 0))
  
  h2oTest.logInfo("Response column is not categorical")
  expect_error(h2o.naiveBayes(x = 1:3, y = 4, training_frame = iris.hex))
  
  
}

h2oTest.doTest("Naive Bayes Test: Test handling of bad initial parameters", test.nbayes.init_err)
