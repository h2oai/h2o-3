setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.rstrip <- function() {
    ##TODO: actually do something here
}

doTest("Test rstrip", test.rstrip)
