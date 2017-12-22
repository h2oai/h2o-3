setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pub_4683 <- function() {
    expect_error(h2o.getFrame("123ABCD"), regex = "Object '123ABCD' not found for argument: key")
}
    

doTest("PUB-4683 Prediction frame can not slice any data", test.pub_4683)

