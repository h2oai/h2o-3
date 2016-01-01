setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




# use this for interactive setup
#    library(h2o)
#    library(testthat)
#    h2o.startLogging()
#    conn = h2o.init()


test.apply <- function() {

    a_initial <- data.frame(
    v1=c(1,0,1,0,1,0,1,0,1,0),
    v2=c(2,2,2,2,2,2,2,2,2,2),
    v3=c(3,3,3,3,3,3,3,3,3,3),
    v4=c(3,2,3,2,3,2,3,2,3,2)
    )

    a <- a_initial
    b <- apply(a, 1, sum)


    a.h2o <- as.h2o(a_initial, destination_frame="r.hex")
    b.h2o <- apply(a.h2o, 1, sum)

    b.h2o.R <- as.matrix(b.h2o)
    b
    b.h2o.R
    expect_that(all(b == b.h2o.R), equals(T))

    b <- apply(a, 2, sum)
    b.h2o <- apply(a.h2o, 2, sum)

    b.h2o.R <- as.matrix(b.h2o)
    b
    b.h2o.R
    expect_that(all(b == b.h2o.R), equals(T))    



    
}

h2oTest.doTest("Test for apply.", test.apply)

