setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.nbayes.bad_data <- function() {
  rawdata <- matrix(rnorm(1000), nrow = 100, ncol = 10)
  
  h2oTest.logInfo("Training data with all NA's")
  train <- matrix(rep(NA, 1000), nrow = 100, ncol = 10)
  allNA.hex <- as.h2o(train)
  expect_error(h2o.naiveBayes(x = 2:10, y = 1, training_frame = allNA.hex))
  
  # Response column must be categorical
  h2oTest.logInfo("Training data with a numeric response column")
  train <- data.frame(rawdata)
  numRes.hex <- as.h2o(train)
  expect_error(h2o.naiveBayes(x = 2:10, y = 1, training_frame = numRes.hex))
  
  # Constant response dropped before model building
  h2oTest.logInfo("Training data with a constant response: drop and throw error")
  train <- data.frame(rawdata)
  train[,1] <- factor("A")
  consRes.hex <- as.h2o(train)
  expect_error(h2o.naiveBayes(x = 2:10, y = 1, training_frame = consRes.hex))
  
  # Predictors with constant value automatically dropped
  h2oTest.logInfo("Training data with 1 col of all 5's: drop automatically")
  train <- data.frame(rawdata); train[,5] <- 5
  train[,1] <- factor(sample(LETTERS[1:4], nrow(rawdata), replace = TRUE))
  colCons.hex <- as.h2o(train)
  expect_warning(fitH2O <- h2o.naiveBayes(x = 2:10, y = 1, training_frame = colCons.hex))
  expect_equal(length(fitH2O@model$pcond), 8)
  
  
}

h2oTest.doTest("Naive Bayes Test: Test handling of bad training data", test.nbayes.bad_data)
