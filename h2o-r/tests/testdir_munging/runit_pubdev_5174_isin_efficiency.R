setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

library(h2o)

test.isin <- function() {
    x = h2o.importFile(locate('smalldata/jira/PUBDEV-5174.csv'), header=TRUE)
    tt = h2o.unique(x$rr) #499851 unique categories
    gg = as.h2o(tt[1:10000,1])  # pulling first 10000 into R 

    ww = x[!(x$rr %in% as.character(as.vector(gg$C1))) , ] #20008 rows do not match

    print(nrow(x))
    print(nrow(tt))
    print(nrow(ww))

    expect_equal(nrow(x), 1000000)
    expect_equal(nrow(tt), 499851)
    expect_equal(nrow(ww), 979992)
}

doTest("Test high cardinality isin()", test.isin)
