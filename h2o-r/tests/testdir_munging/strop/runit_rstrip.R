setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.rstrip <- function() {
    # load iris
    # test non string -> should error
    # test categorical strip -> run, check against exected
    # cast Species column as.character
    # test string strip -> run, check against exected
}

doTest("Test rstrip", test.rstrip)
