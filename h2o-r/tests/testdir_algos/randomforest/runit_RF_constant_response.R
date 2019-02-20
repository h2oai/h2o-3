setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.randomForest.constant_response <- function() {
    train.hex <- h2o.uploadFile(locate("smalldata/iris/iris_train.csv"), "train.hex")
    train.hex$constantCol <- 1

    # Build DRF model, which should run succesfully with constant response when check_constant_response is set to false
    iris.drf.initial <- h2o.randomForest(y = 6, x = 1:5, training_frame = train.hex, check_constant_response = F)
}

doTest("Random Forest test constant response", test.randomForest.constant_response)
