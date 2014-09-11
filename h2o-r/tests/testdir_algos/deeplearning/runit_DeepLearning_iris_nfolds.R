setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.deeplearning.nfolds <- function(conn) {
  iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"), key="iris.hex")
  print(summary(iris.hex))
  iris.nfolds <- h2o.deeplearning(x = 1:4, y = 5, data = iris.hex, nfolds = 3)
  print(iris.nfolds)
  
  # Can't specify both nfolds >= 2 and validation = H2OParsedData at once
  expect_error(h2o.deeplearning(x = 1:4, y = 5, data = iris.hex, nfolds = 5, validation = iris.hex))
  testEnd()
}

doTest("Deep Learning Cross-Validation Test: Iris", test.deeplearning.nfolds)