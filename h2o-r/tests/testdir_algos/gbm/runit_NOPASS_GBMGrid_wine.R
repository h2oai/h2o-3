setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

gbm.grid.test<-
function(conn) {
    wine.hex <- h2o.uploadFile(conn, locate("smalldata/wine.data"), key="wine.hex")
    print(summary(wine.hex))
    x <- 3:14
    wine.grid <- h2o.gbm(y = 2, x = c(1,3:14),
                   loss='AUTO',
                   training_frame = wine.hex, ntrees=c(5,10,15),
                   max_depth=c(2,3,4),
                   learn_rate=c(0.1,0.2))
    print(wine.grid)
    testEnd()
}

doTest("GBM Grid Test: wine.data from smalldata", gbm.grid.test)