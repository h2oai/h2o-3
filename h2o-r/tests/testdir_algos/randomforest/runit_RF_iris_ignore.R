setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.RF.iris_ignore <- function() {
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris22.csv"), "iris.hex")
  h2o.randomForest(y = 5, x = 1:4, training_frame = iris.hex, ntrees = 50, max_depth = 100)
  for (maxx in 1:4) {
    myX     <- seq(1, maxx)
    iris.rf <- h2o.randomForest(y = 5, x = myX, training_frame = iris.hex, ntrees = 50, max_depth = 100)
    print(iris.rf)
  }
  
}

doTest("RF test iris ignore", test.RF.iris_ignore)
