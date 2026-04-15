setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.head_tail <- function() {
  Log.info("Uploading iris/iris_wheader.csv")
  iris.hex <- h2o.importFile(locate("smalldata/iris/iris_wheader.csv"), "iris_wheader.hex")
  iris.dat <- read.csv(locate("smalldata/iris/iris_wheader.csv"), stringsAsFactors=TRUE)
  nrows <- nrow(iris.dat)
  ncols <- ncol(iris.dat)
  
  Log.info("Head and tail with n = 0")
  expect_equal(dim(head(iris.hex, n = 0)), c(0, ncols))
  expect_equal(dim(tail(iris.hex, n = 0)), c(0, ncols))
  
  Log.info(paste("Head and tail with n =", -nrows))
  expect_equal(dim(head(iris.hex, n = -nrows)), c(0, ncols))
  expect_equal(dim(tail(iris.hex, n = -nrows)), c(0, ncols))
  
  slice <- union(c(1, -1, nrows), sample(-nrows:nrows, 10))
  slice <- setdiff(slice, c(-nrows, 0, nrows))
  for(s in slice) {
    Log.info(paste("Head and tail with n =", s))
    expect_equivalent(as.data.frame(head(iris.hex, n = s)), head(iris.dat, n = s))
    expect_equivalent(as.data.frame(tail(iris.hex, n = s)), tail(iris.dat, n = s))
  }
  
}

doTest("Test out head() and tail() functionality", test.head_tail)
