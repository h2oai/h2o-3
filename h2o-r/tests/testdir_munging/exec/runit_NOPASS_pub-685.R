
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# use this for interactive setup
#    library(h2o)
#    library(testthat)
#    h2o.setLogPath(getwd(), "Command")
#    h2o.setLogPath(getwd(), "Error")
#    h2o.startLogging()
#    conn = h2o.init()


test.apply <- function(conn) {

    a_initial = as.data.frame(cbind(
    c(1,0,1,0,1,0,1,0,1,0),
    c(2,2,2,2,2,2,2,2,2,2),
    c(3,3,3,3,3,3,3,3,3,3),
    c(3,2,3,2,3,2,3,2,3,2)
    ))

    a = a_initial
    b = apply(a, c(1,2), sum)


    a.h2o <- as.h2o(conn, a_initial, key="r.hex")
    b.h2o = apply(a.h2o, c(1,2), sum)

    b.h2o.R = as.matrix(b.h2o)
    b
    b.h2o.R
    expect_that(all(b == b.h2o.R), equals(T))

    testEnd()
}

doTest("Test for apply.", test.apply)

