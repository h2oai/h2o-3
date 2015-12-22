setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.RF.nfolds <- function() {
    iris.hex <- h2o.uploadFile( locate("smalldata/iris/iris.csv"),
                               "iris.hex")
    iris.nfolds <- h2o.randomForest(y = 5, x = 1:4, training_frame = iris.hex,
                                    ntrees = 50, nfolds = 5)

    # Can't specify both nfolds >= 2 and validtaion at same time
    expect_error(h2o.randomForest(y = 5, x = 1:4, training_frame = iris.hex,
                                  ntrees = 50, nfolds = 5,
                                  validation_frame = iris.hex))
}

doTest("RF Cross-Validtaion Test: Iris", test.RF.nfolds)
