setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

gbm.grid.test<-
function(conn) {
    wine.hex <- h2o.uploadFile(conn, locate("smalldata/wine.data"), key="wine.hex")
    print(summary(wine.hex))
    x <- 3:14
    wine.grid <- h2o.gbm(y = 2, x = c(1,3:14),
                   distribution='gaussian',
                   data = wine.hex, n.trees=c(5,10,15),
                   interaction.depth=c(2,3,4),
                   shrinkage=c(0.1,0.2))
    print(wine.grid)
    testEnd()
}

doTest("GBM Grid Test: wine.data from smalldata", gbm.grid.test)

