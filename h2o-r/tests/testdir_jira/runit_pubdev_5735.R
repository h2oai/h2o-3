setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.gbm.trees <- function() {
  airlines.data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
  gbm.model = h2o.gbm(x=c("Origin", "Dest", "Distance"),y="IsDepDelayed",training_frame=airlines.data ,model_id="gbm_trees_model", ntrees = 1)
  gbm.tree <-h2o.getModelTree(gbm.model, 1, 1) # Model only has one tree. If these numbers are changed, tests fails. This ensures index translation between R API and Java works.
  
  expect_equal("H2OTree", class(gbm.tree)[1])
  expect_equal("integer", class(gbm.tree@left_children)[1])
  expect_equal("integer", class(gbm.tree@right_children)[1])
  expect_equal("character", class(gbm.tree@descriptions)[1])
  expect_equal("character", class(gbm.tree@model_name)[1])
  expect_equal("integer", class(gbm.tree@tree_number)[1])
  expect_equal("integer", class(gbm.tree@tree_class)[1])
  expect_equal("integer", class(gbm.tree@root_node_id)[1])
  
  expect_equal(length(gbm.tree@left_children)[1], length(gbm.tree@right_children)[1])
  expect_true(is.na(match(0, gbm.tree@left_children)[1])) # There are no zeros in the list of nodes
  expect_true(is.na(match(0, gbm.tree@right_children)[1])) # There are no zeros in the list of nodes
  
  totalLength <- length(gbm.tree@left_children)
  expect_equal(totalLength, length(gbm.tree@descriptions))
  
  # All descriptions must be non-empty
  for (description in gbm.tree@descriptions) {
    expect_false(identical(description, ""))
  }
  
  # First node's description is root node
  expect_equal("Root node", gbm.tree@descriptions[1])
  
  expect_equal(1, gbm.tree@tree_number)
  expect_equal(1, gbm.tree@tree_class)
  
  # DRF model test
  
  drf.model = h2o.randomForest(x=c("Origin", "Dest", "Distance"),y="IsDepDelayed",training_frame=airlines.data ,model_id="gbm_trees_model", ntrees = 1)
  drf.tree <-h2o.getModelTree(drf.model, 1, 1) # Model only has one tree. If these numbers are changed, tests fails. This ensures index translation between R API and Java works.
  
  expect_equal("H2OTree", class(drf.tree)[1])
  expect_equal("integer", class(drf.tree@left_children)[1])
  expect_equal("integer", class(drf.tree@right_children)[1])
  expect_equal("character", class(drf.tree@descriptions)[1])
  expect_equal("character", class(drf.tree@model_name)[1])
  expect_equal("integer", class(drf.tree@tree_number)[1])
  expect_equal("integer", class(drf.tree@tree_class)[1])
  expect_equal("integer", class(drf.tree@root_node_id)[1])
  
  expect_equal(length(drf.tree@left_children)[1], length(drf.tree@right_children)[1])
  expect_true(is.na(match(0, drf.tree@left_children)[1])) # There are no zeros in the list of nodes
  expect_true(is.na(match(0, drf.tree@right_children)[1])) # There are no zeros in the list of nodes
  
  totalLength <- length(drf.tree@left_children)
  expect_equal(totalLength, length(drf.tree@descriptions))
  
  # All descriptions must be non-empty
  for (description in drf.tree@descriptions) {
    expect_false(identical(description, ""))
  }
  
  # First node's description is root node
  expect_equal("Root node", drf.tree@descriptions[1])
  
  expect_equal(1, drf.tree@tree_number)
  expect_equal(1, drf.tree@tree_class)
  
}

doTest("GBM & DRF tree fetching", test.gbm.trees)