setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.RF.iris_class <- function() {
  iris.hex <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris22.csv"), "iris.hex")
  iris.rf  <- h2o.randomForest(y = 5, x = 1:4, training_frame = iris.hex,
                               ntrees = 50, max_depth = 100)
  print(iris.rf)
  iris.rf  <- h2o.randomForest(y = 6, x = 1:4, training_frame = iris.hex,
                               ntrees = 50, max_depth = 100)
  print(iris.rf)
  
}

h2oTest.doTest("RF test iris all", test.RF.iris_class)
