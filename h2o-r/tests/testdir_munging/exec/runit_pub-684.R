
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# use this for interactive setup
#    library(h2o)
#    library(testthat)
#    h2o.setLogPath(getwd(), "Command")
#    h2o.setLogPath(getwd(), "Error")
#    h2o.startLogging()
#    conn = h2o.init()


test.ifelse <- function(conn) {

    a_initial = as.data.frame(cbind(
    c(1,0,1,0,1,0,1,0,1,0),
    c(2,2,2,2,2,2,2,2,2,2),
    c(3,3,3,3,3,3,3,3,3,3),
    c(3,2,3,2,3,2,3,2,3,2)
    ))

    a = a_initial
    b = ifelse(a[,1], a[,3], a[,2])
    expect_that(all(b == a[,4]), equals(T))


    a.h2o <- as.h2o(conn, a_initial, key="r.hex")
    b.h2o = ifelse(a.h2o[,1], a.h2o[,3], a.h2o[,2])

    b.h2o.R = as.matrix(b.h2o)
    b
    b.h2o.R
    expect_that(all(b == b.h2o.R), equals(T))

    testEnd()
}

doTest("Test for ifelse.", test.ifelse)

