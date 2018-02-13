setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.frameslice <- function() {
  expect_true(h2o.xgboost.available())
  Log.info("Importing prostate data...\n")
  pros.hex <- h2o.importFile(path = locate("smalldata/logreg/prostate.csv"))

  Log.info("Running XGBoost on a sliced data frame...\n")
  pros.hex[,2] = as.factor(pros.hex[,2])
  pros.xgboost <- h2o.xgboost(x = 2:8, y = 1, training_frame = pros.hex[, 2:9], distribution = "bernoulli")

  
}

doTest("XGBoost Test: Model building on sliced h2o frame", test.XGBoost.frameslice)
