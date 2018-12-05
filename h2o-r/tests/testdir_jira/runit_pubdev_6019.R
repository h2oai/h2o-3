setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.timestamp <- function() {
  prostate.data <- h2o.importFile(path = locate('smalldata/logreg/prostate_train.csv'))
  gbm.model <- h2o.gbm(x=c("AGE","RACE","DPROS"),y="CAPSULE",training_frame=prostate.data ,model_id="gbm_prostate_model", ntrees = 1, max_depth = 1, seed = 1)
  expect_false(is.null(gbm.model@model$timestamp))

}

doTest("H2OTree visitor", test.timestamp)
