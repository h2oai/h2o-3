setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.assign.slice.large <- function() {
    train.frame <- h2o.importFile(locate("bigdata/laptop/mnist/train.csv.gz"))
    test.frame <- h2o.importFile(locate("bigdata/laptop/mnist/test.csv.gz"))

    lower.idx <- floor(nrow(train.frame) / 2)
    upper.idx <- lower.idx + nrow(test.frame) - 1

    train.frame[lower.idx:upper.idx, ] <- test.frame

    expect_equal(as.data.frame(train.frame[lower.idx:upper.idx, ]), as.data.frame(test.frame))
}

doTest("Test row-slice assign on large & wide data", test.assign.slice.large)