setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.ExtendedIsolationForest.scoring_history <- function() {
    single_blob.hex <-
      h2o.importFile(path = locate("smalldata/anomaly/single_blob.csv"),
                   destination_frame = "single_blob.hex")

    exisofor.model <- h2o.extendedIsolationForest(training_frame = single_blob.hex, score_each_iteration=TRUE, ntrees=10)
    print(exisofor.model)
    expect_equal(nrow(h2o.scoreHistory(exisofor.model)), 11)

    exisofor.model <- h2o.extendedIsolationForest(training_frame = single_blob.hex, score_tree_interval=3, ntrees=10)
    print(exisofor.model)
    expect_equal(nrow(h2o.scoreHistory(exisofor.model)), 5)
}

doTest("ExtendedIsolationForest: Smoke Test For Scoring History", test.ExtendedIsolationForest.scoring_history)
