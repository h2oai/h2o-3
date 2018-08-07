setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.gbm.trees <- function() {
  airlines.data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
  gbm.model = h2o.gbm(x=c("Origin", "Dest", "Distance"),y="IsDepDelayed",training_frame=airlines.data ,model_id="gbm_trees_model", ntrees = 1)
  tree <-h2o.getModelTree(gbm.model, 1, 1) # Model only has one tree. If these numbers are changed, tests fails. This ensures index translation between R API and Java works.
  
  expect_equal("H2OTree", class(tree)[1])
  expect_equal("integer", class(tree@left_children)[1])
  expect_equal("integer", class(tree@right_children)[1])
  expect_equal("character", class(tree@descriptions)[1])
  expect_equal("character", class(tree@model_name)[1])
  expect_equal("integer", class(tree@tree_number)[1])
  expect_equal("integer", class(tree@tree_class)[1])
  
  expect_equal(length(tree@left_children)[1], length(tree@right_children)[1])
  expect_true(is.na(match(0, tree@left_children)[1])) # There are no zeros in the list of nodes
  expect_true(is.na(match(0, tree@right_children)[1])) # There are no zeros in the list of nodes
  
  totalLength <- length(tree@left_children)
  expect_equal(totalLength, length(tree@descriptions))
  
  # All descriptions must be non-empty
  for (description in tree@descriptions) {
    expect_false(identical(description, ""))
  }
  
  # First node's description is root node
  expect_equal("Root node", tree@descriptions[1])
  
  expect_equal(0, tree@tree_number)
  expect_equal(0, tree@tree_class)
  
}

doTest("PUBDEV-5732: Make GBM tree traversal and information accessible from the clients (R/Python)", test.gbm.trees)