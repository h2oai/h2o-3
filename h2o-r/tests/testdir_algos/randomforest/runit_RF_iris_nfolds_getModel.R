setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.RF.nfolds <- function() {
  iris.hex <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris.csv"),
                             "iris.hex")
  iris.nfolds <- h2o.randomForest(y = 5, x = 1:4, training_frame = iris.hex,
                                  ntrees = 50, nfolds = 5)
  print(iris.nfolds)

  iris.nfolds <- h2o.getModel(iris.nfolds@model_id)

  print(iris.nfolds)

  print("")

  h2o.randomForest(y = 5, x = 1:4, training_frame = iris.hex,
                   ntrees = 50, nfolds = 5,
                   validation_frame = iris.hex)
  
}

h2oTest.doTest("RF Cross-Validation Test: Iris", test.RF.nfolds)
