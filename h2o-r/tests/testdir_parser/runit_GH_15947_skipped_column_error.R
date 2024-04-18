setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.skipped_columns <- function() {
  iris_hf <- as.h2o(iris, skipped_columns=c(1,2))
  expect_true(ncol(iris_hf) == (ncol(iris)-2))
  print("Columns are skipped!!!")
}

doTest("Test skipped_columns when using as.h2o to change data frame to H2O Frame.", test.skipped_columns)
