setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.head_empty_frame <- function() {

  hex <- h2o.importFile(locate("smalldata/iris/iris_train.csv"))
  # keep only numeric columns
  num.cols <- c("sepal_len", "sepal_wid", "petal_len", "petal_wid")
  hex <- hex[, num.cols]

  hex[1,] <- 3.3

  expect_equal(as.data.frame(hex[1, ]), data.frame(sepal_len = 3.3, sepal_wid = 3.3, petal_len = 3.3, petal_wid = 3.3))
}

doTest("Test frame add.", test.head_empty_frame)
