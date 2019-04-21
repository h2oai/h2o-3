setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.test_KDD_trees <- function(){
    Log.info("Test to verify that identical models produce idential MSEs")

    cup98LRN_z = h2o.uploadFile(path = locate("bigdata/laptop/usecases/cup98LRN_z.csv"))
    cup98LRN_z$DOB <- as.factor(cup98LRN_z$DOB)
    
    cup98VAL_z = h2o.uploadFile(path = locate("bigdata/laptop/usecases/cup98VAL_z.csv"))
    cup98VAL_z$DOB <- as.factor(cup98VAL_z$DOB)
    
    # keep only minimum number of columns
    keep <- c('LASTGIFT', 'DOB', 'TARGET_D')
    cup98Test <- cup98VAL_z[, keep]
    cup98Train <- cup98LRN_z[, keep]
    
    len <- length(keep) -1 
    
    # using minimum number of tree/max depth to get consistent error reproducibility
    test1 <- h2o.xgboost(x = 1:len, y = 'TARGET_D', training_frame =  cup98Train, ntrees = 2, max_depth = 8, distribution = "gaussian")
    test2 <- h2o.xgboost(x = 1:len, y = 'TARGET_D', training_frame =  cup98Train, ntrees = 2, max_depth = 8, distribution = "gaussian")
    
    Log.info(paste("Test1 MSEs:", test1@model$mse_train))
    Log.info(paste("Test2 MSEs:", test2@model$mse_train))

    expect_equal(test1@model$mse_train, test2@model$mse_train, tolerance = 0.0001)

    
}

doTest("XGBoost Test: KDD trees", check.test_KDD_trees)
