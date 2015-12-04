setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

rf.vi.test<-
function() {
    data.hex <- h2o.uploadFile( locate("smalldata/gbm_test/toy_data_RF.csv"), destination_frame="data.hex")
    print(summary(data.hex))
    x <- 1:6
    y <- 7
    data.hex[,y] <- as.factor(data.hex[,y])
    rf <- h2o.randomForest(x,y,data.hex,ntrees=500, max_depth=20, min_rows=100,
                           seed=1234)
    print(rf@model$variable_importances)
    order(rf@model$variable_importances[1,],decreasing=T)
    expect_equal(order(rf@model$varimp[1,],decreasing=T), c(3,2,6,1,5,4))
}
doTest("Variable Importance RF Test: Weston toy data Smalldata", rf.vi.test)
