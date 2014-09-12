

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# use this for interactive setup
#      library(h2o)
#      library(testthat)
#      h2o.setLogPath(getwd(), "Command")
#      h2o.setLogPath(getwd(), "Error")
#      h2o.startLogging()
#      conn = h2o.init()


test.frame_add <- function(conn) {

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
    d = a + b

    a.h2o <- as.h2o(conn, a_initial, key="cA_0")
    b.h2o <- as.h2o(conn, a_initial, key="cA_1")
    a.h2o + b.h2o
    d.h2o = a.h2o + b.h2o
    d.h2o.R = as.matrix(d.h2o)
    expect_that(all(d == d.h2o.R), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="cA_2")
    b.h2o <- as.h2o(conn, a_initial, key="cA_3")
    a.h2o + b.h2o
    d.h2o = a.h2o + b.h2o
    d.h2o.R = as.matrix(d.h2o)
    expect_that(all(d == d.h2o.R), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="cA_4")
    b.h2o <- as.h2o(conn, a_initial, key="cA_5")
    a.h2o + b.h2o
    d.h2o = a.h2o + b.h2o
    d.h2o.R = as.matrix(d.h2o)
    expect_that(all(d == d.h2o.R), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="cA_6")
    b.h2o <- as.h2o(conn, a_initial, key="cA_7")
    a.h2o + b.h2o
    d.h2o = a.h2o + b.h2o
    d.h2o.R = as.matrix(d.h2o)
    expect_that(all(d == d.h2o.R), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="cA_8")
    b.h2o <- as.h2o(conn, a_initial, key="cA_9")
    a.h2o + b.h2o
    d.h2o = a.h2o + b.h2o
    d.h2o.R = as.matrix(d.h2o)
    expect_that(all(d == d.h2o.R), equals(T))

    testEnd()
}

doTest("Test frame add.", test.frame_add)


