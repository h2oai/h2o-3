setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.km.bad_data <- function() {
  prop <- 0.1
  rawdata <- matrix(rnorm(1000), nrow = 100, ncol = 10)
  
  # Row elements that are NA will be replaced with mean of column
  h2oTest.logInfo("Training data with 1 row of all NAs: replace with column mean")
  train <- rawdata; train[25,] <- NA
  rowNA.hex <- as.h2o( train)
  fitH2O <- h2o.kmeans(rowNA.hex, k = 5)
  expect_equal(dim(getCenters(fitH2O)), c(5,10))
  
  # Columns with constant value will be automatically dropped
  h2oTest.logInfo("Training data with 1 col of all 5's: drop automatically")
  train <- rawdata; train[,5] <- 5
  colCons.hex <- as.h2o( train)
  expect_warning(fitH2O <- h2o.kmeans(colCons.hex, k = 5))
  expect_equal(dim(getCenters(fitH2O)), c(5,9))
  
  h2oTest.logInfo("Training data with 1 col of all NA's, 1 col of all zeroes: drop automatically")
  train <- rawdata; train[,5] <- NA; train[,8] <- 0
  colNA.hex <- as.h2o( train)
  expect_warning(fitH2O <- h2o.kmeans(colNA.hex, k = 5))
  expect_equal(dim(getCenters(fitH2O)), c(5,8))
  
  h2oTest.logInfo("Training data with all NA's")
  train <- matrix(rep(NA, 1000), nrow = 100, ncol = 10)
  allNA.hex <- as.h2o( train)
  expect_error(h2o.kmeans(allNA.hex, k = 5))
  
  h2oTest.logInfo("Training data with a categorical column(s)")
  train <- data.frame(rawdata)
  train[,1] <- factor(sample(LETTERS[1:4], nrow(rawdata), replace = TRUE))
  colFac.hex <- as.h2o( train)
  # expect_error(h2o.kmeans(colFac.hex, k = 5))
  
  h2oTest.logInfo("Importing iris.csv data...\n")
  iris.hex <- h2o.uploadFile( h2oTest.locate("smalldata/iris/iris.csv"))
  fitH2O <- h2o.kmeans(iris.hex, k = 5)
  expect_equal(dim(getCenters(fitH2O)), c(5,5))
  # expect_error(h2o.kmeans(iris.hex, k = 5))
  
  
}

h2oTest.doTest("KMeans Test: Test handling of bad training data", test.km.bad_data)
