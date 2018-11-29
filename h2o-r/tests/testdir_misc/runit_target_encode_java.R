setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
##
# Testing target encoding  (h2o.target_encode_fit and h2o.target_encode_transform)
##


test <- function() {
    #TODO    test case with one te column:  te_cols <- list("cat_column_name")
}

doTest("Test target encoding exposed from Java", test)

