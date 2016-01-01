setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.RF.nfolds <- function() {
    iris.hex <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris.csv"), "iris.hex")
    iris.nfolds <- h2o.randomForest(y = 5, x = 1:4, training_frame = iris.hex, ntrees = 50, nfolds = 5)
    print(iris.nfolds)

    # Can't specify both nfolds >= 2 and validtaion at same time
    iris.valid.nfolds <- h2o.randomForest(y = 5, x = 1:4, training_frame = iris.hex,
                                  ntrees = 50, nfolds = 5, validation_frame = iris.hex)
    print(iris.valid.nfolds)
    
}

h2oTest.doTest("RF Cross-Validtaion Test: Iris", test.RF.nfolds)
