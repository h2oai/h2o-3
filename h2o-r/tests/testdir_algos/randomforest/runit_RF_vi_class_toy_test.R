setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



rf.vi.test<- function() {
    data.hex <- h2o.uploadFile(h2oTest.locate("smalldata/gbm_test/toy_data_RF.csv"), destination_frame="data.hex")
    print(summary(data.hex))
    x <- 1:6
    y <- 7
    data.hex[,y] <- as.factor(data.hex[,y])
    rf <- h2o.randomForest(x,y,data.hex,ntrees=500, max_depth=20, min_rows=50,
                           seed=1234)
    print(rf@model$variable_importances)
    o <- order(rf@model$variable_importances$variable)
    expect_equal(o, c(3,2,1,6,4,5))
    
}
h2oTest.doTest("Variable Importance RF Test: Weston toy data Smalldata", rf.vi.test)
