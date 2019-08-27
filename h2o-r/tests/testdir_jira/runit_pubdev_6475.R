setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.tree.rootnode_only <- function() {
  
  data <- h2o.importFile(locate("smalldata/iris/iris_wheader.csv"))
  model <- h2o.gbm(x= c("sepal_len", "sepal_wid"), y = "class", training_frame = data)
  # Artificially create an XGBoost model with root node only
  model <- h2o.xgboost(x= c("sepal_len", "sepal_wid"), y = "class", training_frame = data, max_depth = 0)
  tree <- h2o.getModelTree(model, 1, "Iris-setosa")
  expect_false(is.null(tree))
  expect_equal(-1, tree@left_children[1])
  expect_equal( -1, tree@right_children[1])
  expect_true(is.null(tree@levels[[1]]))
  expect_true(is.na(tree@thresholds))
  

}

doTest("Decision tree with root node only", test.tree.rootnode_only)
