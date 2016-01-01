setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
test.range.basic <- function() {

  hex <- as.h2o(iris)
  expect_true(all(range(4:8) == c(4,8)))
	expect_true(all(range(iris[,4]) == range(hex[,4])))
	expect_true(all(range(trunc(iris[,4])) == range(trunc(hex[,4]))))
}

h2oTest.doTest("Test the range function", test.range.basic)

