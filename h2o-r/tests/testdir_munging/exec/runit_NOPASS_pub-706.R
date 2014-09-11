
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../findNSourceUtils.R')

# use this for interactive setup
#    library(h2o)
#    library(testthat)
#    h2o.setLogPath(getwd(), "Command")
#    h2o.setLogPath(getwd(), "Error")
#    h2o.startLogging()
#    conn = h2o.init()


test.quoted_comma <- function(conn) {

    a_initial = as.data.frame(cbind(
    c("a,b","c,d", "e,f"),
    c("b","d", "f"),
    c("b","d", "f"),
    c("a,b","c,d", "e,f"),
    c("b","d", "f"),
    c("b","d", "f"),
    c("b","d", "f"),
    c("b","d", "f")
    ))

    a = a_initial
    b = a
    expect_that(all(b == a), equals(T))


    a.h2o <- as.h2o(conn, a_initial, key="r.hex")
    b.h2o = a.h2o

    b.h2o.R = as.matrix(b.h2o)
    
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

