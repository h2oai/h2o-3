
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# use this for interactive setup
#        library(h2o)
#        library(testthat)
#        h2o.setLogPath(getwd(), "Command")
#        h2o.setLogPath(getwd(), "Error")
#        h2o.startLogging()
#        conn = h2o.init()

test.null_tofrom <- function(conn) {

    a_initial <- data.frame(
    v1=c(1,0,1,0,1,0,1,0,1,0),
    v2=c(2,2,2,2,2,2,2,2,2,2),
    v3=c(3,3,3,3,3,3,3,3,3,3),
    v4=c(3,2,3,2,3,2,3,2,3,2)
    )
    a <- a_initial
    b <- a$"13"
    a.h2o <- as.h2o(conn, a_initial, key="r.hex")
    b.h2o <- a.h2o$"3" # doesn't exist
    b.h2o.R <- as.matrix(b.h2o)
    b
    # NULL
    b.h2o.R
    expect_that(all(b == b.h2o.R), equals(T)) 
    testEnd()
}

doTest("Test null_tofrom.", test.null_tofrom)

