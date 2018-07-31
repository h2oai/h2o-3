setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.predict_staged_proba <- function() {
    prostate.hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
    prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)

    prostate.gbm.50 <- h2o.gbm(3:9, "CAPSULE", prostate.hex, ntrees = 50, seed = 123)
    predict.50 <- h2o.predict(prostate.gbm.50, prostate.hex)
    prostate.gbm.10 <- h2o.gbm(3:9, "CAPSULE", prostate.hex, ntrees = 10, seed = 123)
    predict.10 <- h2o.predict(prostate.gbm.10, prostate.hex)

    predict.staged <- h2o.predict_staged_proba(prostate.gbm.50, prostate.hex)

    expect_equal(50, ncol(predict.staged))
    expect_equal(nrow(prostate.hex), nrow(predict.staged))

    expected.50 <- as.data.frame(predict.50$p0)
    colnames(expected.50) <- "T50.C1"
    expect_equal(expected.50, as.data.frame(predict.staged$T50.C1)) # T50.C1 == p0

    expected.10 <- as.data.frame(predict.10$p0)
    colnames(expected.10) <- "T10.C1"
    expect_equal(expected.10, as.data.frame(predict.staged$T10.C1)) # T10.C1 == p0
}

doTest("Test predicting staged probabilites with GBM", test.predict_staged_proba)
