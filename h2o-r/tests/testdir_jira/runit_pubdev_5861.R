setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.tree.visitor <- function() {
  airlines.data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
  gbm.model <- h2o.gbm(x=c("Origin", "Dest", "Distance"),y="IsDepDelayed",training_frame=airlines.data ,model_id="gbm_trees_model", ntrees = 1, max_depth = 1, seed = 1)
  gbm.tree <-h2o.getModelTree(gbm.model, 1, "NO")
  expect_true(is.integer(length(gbm.tree)))
  expect_false(is.null(gbm.tree@root_node))
  expect_false(is.na(gbm.tree@root_node))
  
  expect_equal(3, length(gbm.tree@node_ids)) # There is only one level, max_depth = 1
  expect_equal(length(gbm.tree@left_children), length(gbm.tree@node_ids))

  expect_equal(gbm.tree@node_ids[1],gbm.tree@root_node@id)
  expect_equal(gbm.tree@node_ids[2],gbm.tree@root_node@left_child@id)
  expect_equal(gbm.tree@node_ids[3],gbm.tree@root_node@right_child@id)
  
  expect_equal(gbm.tree@root_node@left_levels, gbm.tree@levels[[2]])
  expect_equal(gbm.tree@root_node@right_levels, gbm.tree@levels[[3]])

  
}

doTest("H2OTree visitor", test.tree.visitor)