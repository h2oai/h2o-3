setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.RF.nfolds <- function(conn) {
  iris.hex <- h2o.uploadFile(conn, locate( "smalldata/iris/iris.csv"), "iris.hex")
  iris.nfolds  <- h2o.randomForest(y = 5, x = seq(1,4), data = iris.hex, ntree = 50, nfolds = 5, type="BigData") 
  print(iris.nfolds)
  
  # Can't specify both nfolds >= 2 and validation = H2OParsedData at once
  expect_error(h2o.randomForest(y = 5, x = seq(1,4), data = iris.hex, ntree = 50, nfolds = 5, validation = iris.hex, type = "BigData"))
  testEnd()
}

doTest("RF Cross-Validation Test: Iris", test.RF.nfolds)
