setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")


test.pub_4683 <- function() {

    result <- tryCatch({h2o.getFrame("123ABCD")}, error = function(e){e})
    notFound <- grepl('Object \'123ABCD\' not found for argument: key', result)
    expect_true(notFound)
}

doTest("PUB-4683 Prediction frame can not slice any data", test.pub_4683)

