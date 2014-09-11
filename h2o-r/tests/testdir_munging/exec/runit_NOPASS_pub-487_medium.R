

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

# use this for interactive setup
#      library(h2o)
#      library(testthat)
#      h2o.setLogPath(getwd(), "Command")
#      h2o.setLogPath(getwd(), "Error")
#      h2o.startLogging()
#      conn = h2o.init()


test.frame_add_equal <- function(conn) {

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
    a = a_initial
    b = a_initial

    a.h2o <- as.h2o(conn, a_initial, key="cA_0")
    b.h2o <- as.h2o(conn, a_initial, key="cA_1")
    d = a[1,] + b[1,]
    a.h2o[1,] + b.h2o[1,]
    d.h2o = a.h2o[1,] + b.h2o[1,]
    d.h2o.R = as.matrix(d.h2o)
    expect_that(all(d == d.h2o.R), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="cA_2")
    b.h2o <- as.h2o(conn, a_initial, key="cA_3")
    d = a[,1] + b[,1]
    a.h2o[,1] + b.h2o[,1]
    d.h2o = a.h2o[,1] + b.h2o[,1]
    d.h2o.R = as.matrix(d.h2o)
    expect_that(all(d == d.h2o.R), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="cA_4")
    b.h2o <- as.h2o(conn, a_initial, key="cA_5")
    d = a + b
    a.h2o + b.h2o
    d.h2o = a.h2o + b.h2o
    d.h2o.R = as.matrix(d.h2o)
    expect_that(all(d == d.h2o.R), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="cA_6")
    b.h2o <- as.h2o(conn, a_initial, key="cA_7")
    d = a == b
    a.h2o == b.h2o
    d.h2o = a.h2o == b.h2o
    d.h2o.R = as.matrix(d.h2o)
    expect_that(all(d == d.h2o.R), equals(T))

    testEnd()
}

# doesn't include issues with NAs! 
doTest("Test frame add and equals.", test.frame_add_equal)


