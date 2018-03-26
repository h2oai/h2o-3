setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pubdev.5414 <- function() {

    prostate.hex <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))

    prostate.train <- prostate.hex
    prostate.train$CAPSULE <- as.factor(prostate.train$CAPSULE)
    n <- nrow(prostate.train)

    # Random weights column
    prostate.train$x1 <- as.h2o(rpois(n, rep(2, n)) + 1)  #Random integer-valued (>=1) weights

    y <- "CAPSULE"
    x <- c("AGE", "RACE", "DCAPS", "PSA", "VOL", "DPROS", "GLEASON")

    gbm.hex <- h2o.gbm(x = x, y = y, training_frame = prostate.train, ntrees = 20, weights_column = "x1")

    prostate.test <- prostate.hex
    prostate.test$CAPSULE <- NULL

    # predict without response and weights columns -> expect no warnings
    expect_that(nrow(h2o.predict(gbm.hex, prostate.test)), not(gives_warning()))
}

doTest("Test predicting on a model without weights column", test.pubdev.5414)
