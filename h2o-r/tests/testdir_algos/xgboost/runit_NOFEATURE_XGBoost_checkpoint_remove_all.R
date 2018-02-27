setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



checkpoint.remove.all <- function() {
  expect_true(h2o.xgboost.available())

  iris = h2o.importFile(locate("smalldata/iris/iris.csv"))
  m1 = h2o.xgboost(x=1:4, y=5, training_frame=iris, ntrees=100)

  path = h2o.saveModel(m1, path=sandbox(), force=TRUE)
  h2o.removeAll()
  restored = h2o.loadModel(path)

  # update m1 with new training data
  iris = h2o.importFile(locate("smalldata/iris/iris.csv"))
  m2 = h2o.xgboost(x=1:4, y=5, training_frame=iris, ntrees=200, checkpoint=restored@model_id)

}

doTest("XGBoost checkpoint with remove all", checkpoint.remove.all)
