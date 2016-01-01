setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")




# use this for interactive setup
#     library(h2o)
#     library(testthat)
#     h2o.startLogging()
#     conn = h2o.init()


test.head_empty <- function() {

    a_initial = data.frame(
    v1=c("a,b","c,d", "e,f",  "e,f"),
    v2=c("b","d", "f",  "f"),
    v3=c("b","d", "f",  "f"),
    v4=c("a,b","c,d", "e,f",  "e,f"),
    v5=c("b","d", "f",  "f"),
    v6=c("b","d", "f",  "f"),
    v7=c("b","d", "f",  "f"),
    v8=c("b","d", "f",  "f")
    )

    a <- a_initial
    b <- a
    expect_that(all(b == a), equals(T))
    print(a)
    print(b)

    a.h2o <- as.h2o(a_initial, destination_frame="r.hex")

    # now we'll create an empty b.h2o in h2o
    b.h2o <- a.h2o[a.h2o$v1==32,]
    print(b.h2o)

    # note the row expression is legal for the non-empty data frame
    #   V1 V2 C3 p4
    print(head(a.h2o[1,]))

    # 1 0 0 0 0
    # data frame with 0 columns and 0 rows
    print(head(b.h2o[,1]))
    print(head(b.h2o[1,]))

    
}

h2oTest.doTest("Test for head_empty.", test.head_empty)
