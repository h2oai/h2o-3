setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.monotone <- function() {
  expect_true(h2o.xgboost.available())

  set.seed(123)
  x <- seq(500)/500
  fx <- -x + rnorm(length(x), mean = 0, sd = 0.1)
  data <- data.frame(x = x, y = fx)
  data_hf <- as.h2o(data)

  frames <- h2o.splitFrame(data_hf, ratios = 0.8, seed = 123)

  xgb.mono <- h2o.xgboost(y = "y",
                          training_frame = frames[[1]],
                          tree_method = "exact",
                          monotone_constraints = list(x = -1),
                          validation_frame = frames[[2]], seed = 123)

  preds <- h2o.predict(xgb.mono, frames[[1]])
  expect_false(is.unsorted(rev(as.data.frame(preds)$predict)))

  xgb.free <- h2o.xgboost(y = "y",
                          training_frame = frames[[1]],
                          tree_method = "exact",
                          validation_frame = frames[[2]], seed = 123)

  expect_gt(
    h2o.mse(xgb.free, valid = TRUE),
    h2o.mse(xgb.mono, valid = TRUE)
  )
}

doTest("XGBoost: Monotonic Constraints", test.XGBoost.monotone)

