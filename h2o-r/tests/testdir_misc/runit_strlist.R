setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.str.list <- function() {
    expect_equal(.str.list(NULL), '[]')
    expect_equal(.str.list(NA), '["NA"]')
    expect_equal(.str.list("a"), '["a"]')
    expect_equal(.str.list(1), '["1"]')
    expect_equal(.str.list(c(NA, "a")), '["NA" "a"]')
    expect_equal(.str.list(c(1, 2)), '["1" "2"]')
}

doTest("Test .str.list", test.str.list)
