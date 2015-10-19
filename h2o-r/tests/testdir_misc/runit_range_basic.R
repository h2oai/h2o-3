setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.range.basic <- function() {

  hex <- as.h2o(iris)
  expect_true(all(range(4:8) == c(4,8)))
	expect_true(all(range(iris[,4]) == range(hex[,4])))
	expect_true(all(range(trunc(iris[,4])) == range(trunc(hex[,4]))))
}

doTest("Test the range function", test.range.basic)

