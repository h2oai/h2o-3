setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.row_to_tree_assignment_api <- function() {
    prostate_hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    ntrees = 1

    gbm_model <- h2o.gbm(training_frame = prostate_hex, y = "AGE", max_depth = 5, seed = 42, ntrees = ntrees, sample_rate=0.1)
    frame <- h2o.row_to_tree_assignment(gbm_model, prostate_hex)
    expect_equal(ncol(frame), ntrees + 1)
    expect_equal(nrow(frame), nrow(prostate_hex))
}

doTest("GBM Test: Row to tree assignment R api", test.GBM.row_to_tree_assignment_api)
