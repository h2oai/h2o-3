setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.scalar.nan.is.numeric <- function() {
  fr <- as.h2o(iris)
  h2o.insertMissingValues(fr)
  numeric_NaN <- sum(fr[,1])
  expect_true(is.numeric(numeric_NaN))
}

doTest("NaN is numeric value", test.scalar.nan.is.numeric)
