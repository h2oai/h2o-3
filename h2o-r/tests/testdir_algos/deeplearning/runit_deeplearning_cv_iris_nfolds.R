setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.deeplearning.nfolds <- function() {
  iris.hex <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris.csv"), destination_frame="iris.hex")
  print(summary(iris.hex))
  iris.nfolds <- h2oTest.deeplearning.cv(x = 1:4, y = 5, training_frame = iris.hex, nfolds = 3)
  print(iris.nfolds)
  iris.nfolds.real <- h2o.deeplearning(x = 1:4, y = 5, training_frame = iris.hex, nfolds = 3)
  print(iris.nfolds.real)

  # Can't specify both nfolds >= 2 and validation = H2OParsedData at once
  expect_error(h2oTest.deeplearning.cv(x = 1:4, y = 5, training_frame = iris.hex, nfolds = 5, validation_frame = iris.hex))
  
}

h2oTest.doTest("Deep Learning Cross-Validation Test: Iris", test.deeplearning.nfolds)
