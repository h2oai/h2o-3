setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




# use this for interactive setup
#    library(h2o)
#    library(testthat)
#    h2o.startLogging()
#    conn = h2o.init()


test.quoted_comma <- function() {

    a_initial <- data.frame(cbind(
    v1 = c("a,b","c,d", "e,f", "e,f"),
    v2=c("b","d", "f", "f"),
    v3=c("b","d", "f", "f"),
    v4=c("a,b","c,d", "e,f", "e,f"),
    v5=c("b","d", "f", "f"),
    v6=c("b","d", "f", "f"),
    v7=c("b","d", "f", "f"),
    v8=c("b","d", "f", "f")
    ))

    a <- a_initial
    b <- a
    expect_that(all(b == a), equals(T))
    print("b = a")


    a.h2o <- as.h2o(a_initial, destination_frame="r.hex")
    b.h2o <- a.h2o

    b.h2o.R <- as.data.frame(b.h2o)

    print("b")
    print(b)
    print("b.h2o")
    print(b.h2o)
    print("b.h2o.R")
    print(b.h2o.R)
    expect_that(all(b == b.h2o.R), equals(T))

    
}

h2oTest.doTest("Test for quoted_comma.", test.quoted_comma)
