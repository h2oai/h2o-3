setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.XGBoost.scale_pos_weight <- function() {
  expect_true(h2o.xgboost.available())

  train <- h2o.importFile(locate("smalldata/prostate/prostate.csv"))
  train$CAPSULE <- as.factor(train$CAPSULE)

  xgb <- h2o.xgboost(y = "CAPSULE", training_frame = train, scale_pos_weight = 1.2, ntrees = 1)

  xgb_params <- as.list(t(xgb@model$native_parameters$value))
  names(xgb_params) <- xgb@model$native_parameters$name

  expect_equal("1.2", xgb_params$scale_pos_weight)
}

doTest("XGBoost: Scale Positive Weight", test.XGBoost.scale_pos_weight)
