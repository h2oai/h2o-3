setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.gbm.monotone <- function() {
    set.seed(123)
    x <- seq(500)/500
    fx <- -x + rnorm(length(x), mean = 0, sd = 0.1)
    data <- data.frame(x = x, y = fx)
    data_hf <- as.h2o(data)

    frames <- h2o.splitFrame(data_hf, ratios = 0.8, seed = 123)

    gbm.mono <- h2o.gbm(y = "y",
                        training_frame = frames[[1]],
                        monotone_constraints = list(x = -1),
                        validation_frame = frames[[2]],
                        seed = 123)

    preds <- h2o.predict(gbm.mono, frames[[1]])
    expect_false(is.unsorted(rev(as.data.frame(preds)$predict)))

    gbm.free <- h2o.gbm(y = "y",
                        training_frame = frames[[1]],
                        validation_frame = frames[[2]], seed = 123)

    expect_gt(
        h2o.mse(gbm.free, valid = TRUE),
        h2o.mse(gbm.mono, valid = TRUE)
    )
}

doTest("GBM: Monotonic Constraints", test.gbm.monotone)

