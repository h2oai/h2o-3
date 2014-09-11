setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

gbm.grid.test<-
function(conn) {
    air.hex <- h2o.uploadFile(conn, locate("smalldata/airlines/allyears2k_headers.zip"), key="air.hex")
    print(summary(air.hex))
    myX <- c("DayofMonth", "DayOfWeek")
    air.grid <- h2o.gbm(y = "IsDepDelayed", x = myX, 
                   distribution="multinomial", 
                   data = air.hex, n.trees=c(5,10,15), 
                   interaction.depth=c(2,3,4), 
                   shrinkage=c(0.1,0.2))
    print(air.grid)
    testEnd()
}

doTest("GBM Grid Test: Airlines Smalldata", gbm.grid.test)

