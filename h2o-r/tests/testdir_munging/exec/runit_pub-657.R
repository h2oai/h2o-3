# library(h2o)
# library(testthat)
# conn = h2o.init()

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

test.pub.657 <- function(conn) {

    a_initial = cbind(c(0,0,0,0), c(1,1,1,1))
    a = a_initial

    a.h2o <- as.h2o(conn, a_initial, key="A.hex")
    a.h2o[,1] = c(0)
    a[,1] = c(0)
    a.h2o.R = as.matrix(a.h2o)
    expect_that(all(a == a.h2o.R), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="A.hex")
    a.h2o[,1] = c(1)
    a[,1] = c(1)
    a.h2o.R = as.matrix(a.h2o)
    expect_that(all(a == a.h2o.R), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="A.hex")
    a.h2o[,1] = 0
    a[,1] = 0
    a.h2o.R = as.matrix(a.h2o)
    expect_that(all(a == a.h2o.R), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="A.hex")
    a.h2o[,1] = 1
    a[,1] = 1
    a.h2o.R = as.matrix(a.h2o)
    expect_that(all(a == a.h2o.R), equals(T))

    # a.h2o[,1] = c(0,0)
    # Error in `[<-`(`*tmp*`, , 1, value = c(0, 0)) :
    #   value must be either a single number or a vector of length 4

    testEnd()
}

doTest("Test for pub-657.", test.pub.657)

