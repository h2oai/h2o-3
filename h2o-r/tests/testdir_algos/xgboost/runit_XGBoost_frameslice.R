setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.XGBoost.frameslice <- function() {
  expect_true(h2o.xgboost.available())
  pros.hex <- h2o.importFile(path = locate("smalldata/logreg/prostate.csv"))

  pros.hex[,2] = as.factor(pros.hex[,2])
  pros.xgboost <- h2o.xgboost(x = 2:8, y = 1, training_frame = pros.hex[, 2:9], distribution = "bernoulli")
  model.params <- h2o::getParms(pros.xgboost)
  print(model.params)

  expect_equal(model.params$distribution, 'bernoulli')
  expect_equal(model.params$x, c("AGE","RACE","DPROS","DCAPS","PSA","VOL","GLEASON"))
  expect_equal(model.params$y, "CAPSULE")
  expect_true(grepl(pattern = "XGBoost", model.params$model_id))
  
}

doTest("XGBoost Test: Model building on sliced h2o frame", test.XGBoost.frameslice)
