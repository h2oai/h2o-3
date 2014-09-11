
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

# use this for interactive setup
#     library(h2o)
#     library(testthat)
#     h2o.setLogPath(getwd(), "Command")
#     h2o.setLogPath(getwd(), "Error")
#     h2o.startLogging()
#     conn = h2o.init()


test.apply_w_quantile <- function(conn) {

    a_initial = as.data.frame(cbind(
    c(1,0,1,0,1,0,1,0,1,0),
    c(2,2,2,2,2,2,2,2,2,2),
    c(3,3,3,3,3,3,3,3,3,3),
    c(3,2,3,2,3,2,3,2,3,2)
    ))

    a = a_initial
    # two ways to do it

    # func6 = function(x) { quantile(x[,1] , c(0.9) ) }
    func6 = function(x) { quantile(x , c(0.9) ) }
    # push the function to h2o also!, with same name
    h2o.addFunction(conn, func6)
    b = apply(a, c(2), func6)
    a.h2o <- as.h2o(conn, a_initial, key="r.hex")
    b.h2o = apply(a.h2o, c(2), func6)
    b.h2o.R = as.matrix(b.h2o)
    b
    b.h2o.R
    expect_that(all(b == b.h2o.R), equals(T))

    b = apply(a, c(2), function(x) quantile(x , c(0.9) ))
    a.h2o <- as.h2o(conn, a_initial, key="r.hex")
    b.h2o = apply(a.h2o, c(2), function(x) quantile(x[,1] , c(0.9) ))
    b.h2o.R = as.matrix(b.h2o)
    b
    b.h2o.R
    expect_that(all(b == b.h2o.R), equals(T)) 

    testEnd()
}

doTest("Test for apply with quantile.", test.apply_w_quantile)

