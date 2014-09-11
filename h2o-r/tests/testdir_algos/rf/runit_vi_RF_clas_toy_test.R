setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

rf.vi.test<-
function(conn) {
    data.hex <- h2o.uploadFile(conn, locate("smalldata/toy_data_RF.csv"), key="data.hex")
    print(summary(data.hex))
    x <- 1:6
    y <- 7
    rf <- h2o.randomForest(x,y,data.hex,importance=T, ntree=500, depth=20, nbins=100, type = "BigData")
    print(rf@model$varimp)
    expect_equal(order(rf@model$varimp[1,],decreasing=T), c(3,2,6,5,1,4))
    testEnd()
}
doTest("Variable Importance RF Test: Weston toy data Smalldata", rf.vi.test)





















