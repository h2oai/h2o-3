setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.deeplearning.nfolds <- function(conn) {
  iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"), key="iris.hex")
  iris.hex[,5] <- as.factor(iris.hex[,5])
  print(summary(iris.hex))
  iris.nfolds <- h2o.deeplearning.cv(x = 1:4, y = 5, training_frame = iris.hex, nfolds = 3, loss = "CrossEntropy")
  print(iris.nfolds)
  
  # Can't specify both nfolds >= 2 and validation = H2OParsedData at once
  expect_error(h2o.deeplearning.cv(x = 1:4, y = 5, training_frame = iris.hex, nfolds = 5, validation_frame = iris.hex, loss = "CrossEntropy"))
  testEnd()
}

doTest("Deep Learning Cross-Validation Test: Iris", test.deeplearning.nfolds)