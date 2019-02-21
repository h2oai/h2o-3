setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.GBM.constant_response <- function() {
    train.hex <- h2o.uploadFile(locate("smalldata/iris/iris_train.csv"), "train.hex")
    train.hex$constantCol <- 1
    
    # Build GBM model, which should run successfully with constant response when check_constant_response is set to false
    iris.gbm.initial <- h2o.gbm(y = 6, x = 1:5, training_frame = train.hex, check_constant_response = F)
}

doTest("GBM test constant response", test.GBM.constant_response)



