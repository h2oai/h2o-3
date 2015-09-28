setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.nbayes.init_err <- function() {
  Log.info("Importing iris_wheader.csv data...\n")
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris_wheader.csv"))
  iris.sum <- summary(iris.hex)
  print(iris.sum)
  
  Log.info("Laplace smoothing parameter is negative")
  expect_error(h2o.naiveBayes(x = 1:4, y = 5, training_frame = iris.hex, laplace = -1))
  
  Log.info("Minimum standard deviation is zero")
  expect_error(h2o.naiveBayes(x = 1:4, y = 5, training_frame = iris.hex, min_sdev = 0))
  
  Log.info("Response column is not categorical")
  expect_error(h2o.naiveBayes(x = 1:3, y = 4, training_frame = iris.hex))
  
  
}

doTest("Naive Bayes Test: Test handling of bad initial parameters", test.nbayes.init_err)
