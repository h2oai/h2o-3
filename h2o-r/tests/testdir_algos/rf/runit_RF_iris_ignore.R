setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

test.RF.iris_ignore <- function(conn) {
  iris.hex  <- h2o.uploadFile(conn, locate("smalldata/iris/iris22.csv"), "iris.hex")
  h2o.randomForest(y = 5, x = seq(1,4), data = iris.hex, ntree = 50, depth = 100)
  
  for (maxx in 1:4) {
    
    myX     <- seq(1, maxx)
    iris.rf <- h2o.randomForest(y = 5, x = myX, data = iris.hex, ntree = 50, depth = 100)
    print(iris.rf)
  
  }
  testEnd()
}

doTest("RF test iris ignore", test.RF.iris_ignore)

