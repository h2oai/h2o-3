
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

# use this for interactive setup
#     library(h2o)
#     library(testthat)
#     h2o.setLogPath(getwd(), "Command")
#     h2o.setLogPath(getwd(), "Error")
#     h2o.startLogging()
#     conn = h2o.init()


test.head_empty <- function(conn) {

    a_initial = as.data.frame(
    v1=c("a,b","c,d", "e,f"),
    v2=c("b","d", "f"),
    v3=c("b","d", "f"),
    v4=c("a,b","c,d", "e,f"),
    v5=c("b","d", "f"),
    v6=c("b","d", "f"),
    v7=c("b","d", "f"),
    v8=c("b","d", "f")
    )

    a <- a_initial
    b <- a
    expect_that(all(b == a), equals(T))

    a.h2o <- as.h2o(conn, a_initial, key="r.hex")

    # now we'll create an empty b.h2o in h2o
    b.h2o <- a.h2o[a.h2o$V1==32]

    # note the row expression is legal for the non-empty data frame
    #   V1 V2 C3 p4
    head(a.h2o[1,])

    # 1 0 0 0 0 
    # data frame with 0 columns and 0 rows
    head(b.h2o[,1])
    head(b.h2o[1,])

    b.h2o.R <- as.matrix(b.h2o)
    
    expect_that(all(b == b.h2o.R), equals(T))

    testEnd()
}

doTest("Test for head_empty.", test.head_empty)
