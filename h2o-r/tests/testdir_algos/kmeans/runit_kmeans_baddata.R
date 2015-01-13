setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.km.bad_data <- function(conn) {
  prop <- 0.1
  rawdata <- matrix(rnorm(1000), nrow = 100, ncol = 10)
  
  # Row elements that are NA will be replaced with mean of column
  Log.info("Training data with 1 row of all NAs: replace with column mean")
  train <- rawdata; train[25,] <- NA
  rowNA.hex <- as.h2o(conn, train)
  fitH2O <- h2o.kmeans(rowNA.hex, k = 5)
  expect_equal(dim(fitH2O@model$centers), c(5,10))
  
  # Columns with constant value will be automatically dropped
  Log.info("Training data with 1 column of all NAs: drop automatically")
  train <- rawdata; train[,5] <- NA
  colNA.hex <- as.h2o(conn, train)
  fitH2O <- h2o.kmeans(colNA.hex, k = 5)
  expect_equal(dim(fitH2O@model$centers), c(5,9))
  
  Log.info("Training data with all NAs")
  train <- matrix(rep(NA, 1000), nrow = 100, ncol = 10)
  allNA.hex <- as.h2o(conn, train)
  expect_error(h2o.kmeans(allNA.hex, k = 5))
  
  # Log.info("Training data with categorical column (unimplemented)")
  # iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"))
  # expect_error(h2o.kmeans(iris.hex, k = 5))
  
  testEnd()
}

doTest("KMeans Test: Test handling of bad training data", test.km.bad_data)