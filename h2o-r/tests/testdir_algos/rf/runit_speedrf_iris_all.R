setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.speedrf.iris_class <- function(conn) {
  iris.hex <- h2o.uploadFile(conn, locate( "smalldata/iris/iris22.csv"), "iris.hex")
  iris.rf  <- h2o.randomForest(y = 5, x = 1:4, data = iris.hex, ntree = 50, depth = 100, importance = TRUE) 
  print(iris.rf)
  iris.rf  <- h2o.randomForest(y = 6, x = 1:4, data = iris.hex, ntree = 50, depth = 100, importance = TRUE )
  print(iris.rf)
  testEnd()
}

doTest("speedrf test iris all", test.speedrf.iris_class)

