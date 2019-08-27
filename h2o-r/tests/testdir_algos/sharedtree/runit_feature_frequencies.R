setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.feature_frequencies <- function() {
    prostate_hex <- h2o.importFile(locate('smalldata/testng/prostate.csv'))
    prostate_hex$CAPSULE <- as.factor(prostate_hex$CAPSULE)
    prostate_hex$ID <- NULL

    gbm <- h2o.gbm(training_frame = prostate_hex, y = "CAPSULE")
    ff_gbm <- h2o.feature_frequencies(gbm, prostate_hex)

    expect_equal(nrow(ff_gbm), nrow(prostate_hex))
    expect_equal(ncol(ff_gbm), ncol(prostate_hex)-1)
    
    drf <- h2o.randomForest(training_frame = prostate_hex, y = "CAPSULE")
    ff_drf <- h2o.feature_frequencies(drf, prostate_hex)

    expect_equal(nrow(ff_drf), nrow(prostate_hex))
    expect_equal(ncol(ff_drf), ncol(prostate_hex)-1)

    iforest <- h2o.isolationForest(training_frame = prostate_hex)
    ff_iforest <- h2o.feature_frequencies(iforest, prostate_hex)

    expect_equal(nrow(ff_iforest), nrow(prostate_hex))
    expect_equal(ncol(ff_iforest), ncol(prostate_hex))
}

doTest("Shared Tree: feature_frequencies", test.feature_frequencies)
