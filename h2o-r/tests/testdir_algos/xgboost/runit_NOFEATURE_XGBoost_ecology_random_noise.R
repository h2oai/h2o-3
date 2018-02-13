setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.XGBoost.ecology.random.noise <- function() {
  expect_true(h2o.xgboost.available())

  df <- h2o.uploadFile(locate("smalldata/gbm_test/ecology_model.csv"))
  # pred_noise_bandwidth is not implemented for XGBoost
  model <- h2o.xgboost(x = 3:13, y = "Angaus", training_frame = df, pred_noise_bandwidth=0.5)
  print(model)
}

doTest("XGBoost: Ecology Data", test.XGBoost.ecology.random.noise)

