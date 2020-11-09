setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.h2o.list_models <- function() {
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), "iris.hex")
  model1 <- h2o.gbm(x=c(3,4),y=5,training_frame=iris.hex)
  model2 <- h2o.xgboost(x=c(1,2),y=5,training_frame=iris.hex)
  
  list <- h2o.list_models()
  expect_equal(c(model1@model_id, model2@model_id), list) 
}

doTest("Test h2o.list_models", test.h2o.list_models)
