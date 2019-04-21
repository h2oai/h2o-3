setwd(normalizePath(dirname(R.utils::commandArgs(asValues = TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.5129 <- function() {
    prosPath <- system.file("extdata", "prostate.csv", package = "h2o")
    prostate.hex <- h2o.uploadFile(path = prosPath)
    fit <- h2o.kmeans(training_frame = prostate.hex, k = 10, x = c("AGE", "RACE", "VOL", "GLEASON"), fold_column = "DPROS")
    expect_equal(attr(x = fit, "allparameters")$fold_column$column_name, "DPROS")
    expect_true(all(attr(x = fit, "allparameters")$x == c("AGE", "RACE", "VOL", "GLEASON")))
}

doTest("PUBDEV-5129: Fold column in Kmeans should not be required to be in x", test.pubdev.5129)
