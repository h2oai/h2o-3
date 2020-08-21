setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.model_timing_attributes <- function() {
  fr <- h2o.importFile(locate("smalldata/logreg/prostate_train.csv"))
  target <- "CAPSULE"
  fr[target] <- as.factor(fr[target])

  gbm <- h2o.gbm(model_id = "R_test_model_output_timing",
                 y = target,
                 training_frame = fr)

  expect_gt(gbm@model$start_time, 0)
  expect_gt(gbm@model$end_time, 0)
  expect_gt(gbm@model$run_time, 0)
}

doTest("Test model exposes timing properties", test.model_timing_attributes)
