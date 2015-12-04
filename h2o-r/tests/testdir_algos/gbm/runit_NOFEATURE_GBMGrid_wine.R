setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

gbm.grid.test<-
function() {
    wine.hex <- h2o.uploadFile( locate("smalldata/gbm_test/wine.data"), destination_frame="wine.hex")
    print(summary(wine.hex))
    x <- 3:14
    wine.grid <- h2o.gbm(y = 2, x = c(1,3:14),
                   distribution='gaussian',
                   training_frame = wine.hex, ntrees=c(5,10,15),
                   max_depth=c(2,3,4),
                   learn_rate=c(0.1,0.2))
    print(wine.grid)
}

doTest("GBM Grid Test: wine.data from smalldata", gbm.grid.test)
