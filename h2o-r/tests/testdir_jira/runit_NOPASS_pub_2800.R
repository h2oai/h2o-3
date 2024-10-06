setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
# Test to make sure that you do not lose a RTMP frame in H2O when you execute an erroneous line of R code
# R behavior: Reports an error but keeps the frame as is

test.pubdev.2800 <- function(conn){
    df <- h2o.importFile("smalldata/jira/test_string_missing.csv")
    expect_false(is.na(df[3,2]))
}

doTest("'0' Parsed incorrectly", test.pubdev.2800)
