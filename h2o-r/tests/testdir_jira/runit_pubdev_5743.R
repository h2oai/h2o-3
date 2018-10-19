setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.tree.visitor <- function() {
  airlines.data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
  xgboost.model <- h2o.xgboost(x=c("Origin", "Dest", "Distance"),y="IsDepDelayed",training_frame=airlines.data ,model_id="gbm_trees_model", ntrees = 1, max_depth = 1, seed = 1)
  xgboost.tree <-h2o.getModelTree(xgboost.model, 1, "NO")
  expect_true(is.integer(length(xgboost.tree)))
  expect_false(is.null(xgboost.tree@root_node))
  expect_false(is.na(xgboost.tree@root_node))
  
  expect_equal(3, length(xgboost.tree@node_ids)) # There is only one level, max_depth = 1
  expect_equal(length(xgboost.tree@left_children), length(xgboost.tree@node_ids))

  expect_equal(xgboost.tree@node_ids[1],xgboost.tree@root_node@id)
  expect_equal(xgboost.tree@node_ids[2],xgboost.tree@root_node@left_child@id)
  expect_equal(xgboost.tree@node_ids[3],xgboost.tree@root_node@right_child@id)

}

doTest("H2OTree visitor", test.tree.visitor)