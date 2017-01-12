setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.string.grep <- function() {
  strings <- as.h2o(c("RST", "rst", "ABC", "abc"))

  Log.info("Grep, basic params")
  positions <- h2o.grep("[a-d]+", strings)
  expect_equal(as.data.frame(positions), data.frame(C1 = 4))

  Log.info("Grep, logical output")
  logicals <- h2o.grep("[a-d]+", strings, output.logical = TRUE)
  expect_equal(as.data.frame(logicals), data.frame(C1 = c(0, 0, 0, 1)))

  Log.info("Grep, ignore case")
  positions.ic <- h2o.grep("[a-d]+", strings, ignore.case = TRUE)
  expect_equal(as.data.frame(positions.ic), data.frame(C1 = c(3, 4)))

  Log.info("Grep, invert")
  positions.inv <- h2o.grep("[a-d]+", strings, invert = TRUE)
  expect_equal(as.data.frame(positions.inv), data.frame(C1 = c(1, 2, 3)))

  Log.info("Grep, all options")
  result <- h2o.grep("[a-d]+", strings, ignore.case = TRUE, invert = TRUE, output.logical = TRUE)
  expect_equal(as.data.frame(result), data.frame(C1 = c(1, 1, 0, 0)))
}

doTest("Testing Grep Search on Strings", test.string.grep)
