setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




# use this for interactive setup
#     library(h2o)
#     library(testthat)
#     h2o.startLogging()
#     conn = h2o.init()


test.apply_w_quantile <- function() {

    a_initial <- data.frame(cbind(
    v1=c(1,0,1,0,1,0,1,0,1,0),
    v2=c(2,2,2,2,2,2,2,2,2,2),
    v3=c(3,3,3,3,3,3,3,3,3,3),
    v4=c(3,2,3,2,3,2,3,2,3,2)
    ))

    a <- a_initial
    # two ways to do it

    # func6 = function(x) { quantile(x[,1] , c(0.9) ) }
    func6 <- function(x) { quantile(x , c(0.9) ) }
    # push the function to h2o also!, with same name
    # h2o.addFunction(func6)
    b = apply(a, c(2), func6)
    a.h2o <- as.h2o(a_initial, destination_frame="r.hex")
    b.h2o = apply(a.h2o, c(2), func6)
    b.h2o.R = as.matrix(b.h2o)
    b
    b.h2o.R
    expect_that(all(b == b.h2o.R), equals(T))

    b = apply(a, c(2), function(x) quantile(x , c(0.9) ))
    a.h2o <- as.h2o(a_initial, destination_frame="r.hex")
    b.h2o = apply(a.h2o, c(2), function(x) quantile(x[,1] , c(0.9) ))
    b.h2o.R = as.matrix(b.h2o)
    b
    b.h2o.R
    expect_that(all(b == b.h2o.R), equals(T)) 

    
}

h2oTest.doTest("Test for apply with quantile.", test.apply_w_quantile)

