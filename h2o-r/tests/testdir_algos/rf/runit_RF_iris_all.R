setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.RF.iris_class <- function(conn) {
  iris.hex <- h2o.uploadFile(conn, locate( "smalldata/iris/iris22.csv"), "iris.hex")
  iris.rf  <- h2o.randomForest(y = 5, x = seq(1,4), data = iris.hex, ntree = 50, depth = 100, type = "BigData")
  print(iris.rf)
  iris.rf  <- h2o.randomForest(y = 6, x = seq(1,4), data = iris.hex, ntree = 50, depth = 100, type = "BigData")
  print(iris.rf)
  testEnd()
}

doTest("RF test iris all", test.RF.iris_class)

