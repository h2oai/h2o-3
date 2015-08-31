

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

# use this for interactive setup
#      library(h2o)
#      library(testthat)
#      h2o.startLogging()
#      conn = h2o.init()


test.frame_add <- function() {

    a_initial = cbind(
    c(0,0,1,0,0,1,0,0,0,0),
    c(1,1,1,0,1,0,1,0,1,0),
    c(1,0,1,0,1,0,1,0,0,1),
    c(1,1,0,0,0,1,0,0,0,1),
    c(1,1,1,0,1,0,0,0,1,1),
    c(1,0,1,0,0,0,0,0,1,1),
    c(1,1,1,0,0,0,1,1,1,0),
    c(0,0,1,1,1,0,0,1,1,0),
    c(0,1,1,1,1,0,0,1,1,0),
    c(0,0,0,0,0,1,1,0,0,0)
    )
    
    a.h2o <- as.h2o(a_initial, destination_frame="cA_0")
    b.h2o <- as.h2o(a_initial, destination_frame="cA_1")

    Log.info("Try a.h2o[1,] + b.h2o[1,]")
    res <- a.h2o[1,] + b.h2o[1,]

    Log.info("Try a.h2o[,1] + b.h2o[,1]")
    res2 <- a.h2o[,1] + b.h2o[,1]
  
    Log.info("Try a.h2o + b.h2o")
    res3 <- a.h2o + b.h2o

    Log.info("Try a.h2o == b.h2o")
    res4 <- a.h2o == b.h2o
      
  
    testEnd()
}

doTest("Test frame add.", test.frame_add)


