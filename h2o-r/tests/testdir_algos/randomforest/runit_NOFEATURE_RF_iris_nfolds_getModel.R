setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.RF.nfolds <- function(conn) {
  iris.hex <- h2o.uploadFile(conn, locate("smalldata/iris/iris.csv"),
                             "iris.hex")
  iris.nfolds <- h2o.randomForest(y = 5, x = 1:4, training_frame = iris.hex,
                                  ntrees = 50, nfolds = 5)
  print(iris.nfolds)

  iris.nfolds <- h2o.getModel(conn, iris.noflds@model_id)

  print(iris.nfolds)

  print("")

  # Can't specify both nfolds >= 2 and validation at same time
  expect_error(h2o.randomForest(y = 5, x = 1:4, training_frame = iris.hex,
                                ntrees = 50, nfolds = 5,
                                validation_frame = iris.hex))
  testEnd()
}

doTest("RF Cross-Validation Test: Iris", test.RF.nfolds)
