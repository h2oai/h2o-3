
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# use this for interactive setup
#    library(h2o)
#    library(testthat)
#    h2o.startLogging()
#    conn = h2o.init()


test.quoted_comma <- function(conn) {

    a_initial <- data.frame(cbind(
    v1 = c("a,b","c,d", "e,f"),
    v2=c("b","d", "f"),
    v3=c("b","d", "f"),
    v4=c("a,b","c,d", "e,f"),
    v5=c("b","d", "f"),
    v6=c("b","d", "f"),
    v7=c("b","d", "f"),
    v8=c("b","d", "f")
    ))

    a <- a_initial
    b <- a
    expect_that(all(b == a), equals(T))


    a.h2o <- as.h2o(conn, a_initial, key="r.hex")
    b.h2o <- a.h2o

    b.h2o.R <- as.matrix(b.h2o)
    
    print("b")
    print(b)
    print("b.h2o")
    print(b.h2o)
    print("b.h2o.R")
    print(b.h2o.R)
    expect_that(all(b == b.h2o.R), equals(T))

    testEnd()
}

doTest("Test for quoted_comma.", test.quoted_comma)
