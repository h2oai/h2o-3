setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.gbm.trees <- function() {
  airlines.data <- h2o.importFile(path = locate('smalldata/testng/airlines_train.csv'))
  gbm.model = h2o.gbm(x=c("Origin", "Dest", "Distance"),y="IsDepDelayed",training_frame=airlines.data ,model_id="gbm_trees_model", ntrees = 1)
  gbm.tree <-h2o.getModelTree(gbm.model, 1, "Yes") # Model only has one tree. If these numbers are changed, tests fails. This ensures index translation between R API and Java works.
  
  expect_equal("H2OTree", class(gbm.tree)[1])
  expect_equal("integer", class(gbm.tree@left_children)[1])
  expect_equal("integer", class(gbm.tree@right_children)[1])
  expect_equal("numeric", class(gbm.tree@thresholds)[1])
  expect_equal("character", class(gbm.tree@features)[1])
  expect_equal("logical", class(gbm.tree@nas)[1])
  expect_equal("character", class(gbm.tree@descriptions)[1])
  expect_equal("character", class(gbm.tree@model_id)[1])
  expect_equal("integer", class(gbm.tree@tree_number)[1])
  expect_equal("character", class(gbm.tree@tree_class)[1])
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
  expect_equal("NO", gbm.tree@tree_class)
  
  # DRF model test
  
  drf.model = h2o.randomForest(x=c("Origin", "Dest", "Distance"),y="IsDepDelayed",training_frame=airlines.data ,model_id="gbm_trees_model", ntrees = 1)
  drf.tree <-h2o.getModelTree(drf.model, 1) # Model only has one tree. If these numbers are changed, tests fails. This ensures index translation between R API and Java works.
  
  expect_equal("H2OTree", class(drf.tree)[1])
  expect_equal("integer", class(drf.tree@left_children)[1])
  expect_equal("integer", class(drf.tree@right_children)[1])
  expect_equal("numeric", class(drf.tree@thresholds)[1])
  expect_equal("character", class(drf.tree@features)[1])
  expect_equal("logical", class(drf.tree@nas)[1])
  expect_equal("character", class(drf.tree@descriptions)[1])
  expect_equal("character", class(drf.tree@model_id)[1])
  expect_equal("integer", class(drf.tree@tree_number)[1])
  expect_equal("character", class(drf.tree@tree_class)[1]) # The value must be properly filled by the backend, even if unspecified
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
  expect_equal("NO", drf.tree@tree_class)
  
  # Cars test - multinomial
  cars.data <- h2o.importFile(path = locate('smalldata/junit/cars_nice_header.csv'))
  cars.data['cylinders'] <- h2o.asfactor(cars.data['cylinders'])
  multinomial.model = h2o.randomForest(x=c("power", "acceleration"),y="cylinders",training_frame=cars.data ,model_id="gbm_trees_model", ntrees = 1)
  multinomial.tree <-h2o.getModelTree(multinomial.model, 1, "4") # Model only has one tree. If these numbers are changed, tests fails. This ensures index translation between R API and Java works.
  
  expect_equal("H2OTree", class(multinomial.tree)[1])
  expect_equal("integer", class(multinomial.tree@left_children)[1])
  expect_equal("integer", class(multinomial.tree@right_children)[1])
  expect_equal("numeric", class(multinomial.tree@thresholds)[1])
  expect_equal("character", class(multinomial.tree@features)[1])
  expect_equal("logical", class(multinomial.tree@nas)[1])
  expect_equal("character", class(multinomial.tree@descriptions)[1])
  expect_equal("character", class(multinomial.tree@model_id)[1])
  expect_equal("integer", class(multinomial.tree@tree_number)[1])
  expect_equal("character", class(multinomial.tree@tree_class)[1]) # The value must be properly filled by the backend, even if unspecified
  expect_equal("integer", class(multinomial.tree@root_node_id)[1])
  
  expect_equal(length(multinomial.tree@left_children)[1], length(multinomial.tree@right_children)[1])
  expect_true(is.na(match(0, multinomial.tree@left_children)[1])) # There are no zeros in the list of nodes
  expect_true(is.na(match(0, multinomial.tree@right_children)[1])) # There are no zeros in the list of nodes
  
  totalLength <- length(multinomial.tree@left_children)
  expect_equal(totalLength, length(multinomial.tree@descriptions))
  
  # All descriptions must be non-empty
  for (description in multinomial.tree@descriptions) {
    expect_false(identical(description, ""))
  }
  
  # First node's description is root node
  expect_equal("Root node", multinomial.tree@descriptions[1])
  
  expect_equal(1, multinomial.tree@tree_number)
  expect_equal("4", multinomial.tree@tree_class)
  
  
  # Cars test - regression
  regression.model = h2o.randomForest(x=c("cylinders", "acceleration"),y="power",training_frame=cars.data ,model_id="gbm_trees_model", ntrees = 1)
  expect_equal("Regression", regression.model@model$training_metrics@metrics$model_category)
  regression.tree <-h2o.getModelTree(regression.model, 1) # Model only has one tree. If these numbers are changed, tests fails. This ensures index translation between R API and Java works.
  
  expect_equal("H2OTree", class(regression.tree)[1])
  expect_equal("integer", class(regression.tree@left_children)[1])
  expect_equal("integer", class(regression.tree@right_children)[1])
  expect_equal("numeric", class(regression.tree@thresholds)[1])
  expect_equal("character", class(regression.tree@features)[1])
  expect_equal("logical", class(regression.tree@nas)[1])
  expect_equal("character", class(regression.tree@descriptions)[1])
  expect_equal("character", class(regression.tree@model_id)[1])
  expect_equal("integer", class(regression.tree@tree_number)[1])
  expect_equal("character", class(regression.tree@tree_class)[1])
  expect_equal("integer", class(regression.tree@root_node_id)[1])
  
  expect_equal(length(regression.tree@left_children)[1], length(regression.tree@right_children)[1])
  expect_true(is.na(match(0, regression.tree@left_children)[1])) # There are no zeros in the list of nodes
  expect_true(is.na(match(0, regression.tree@right_children)[1])) # There are no zeros in the list of nodes
  
  totalLength <- length(regression.tree@left_children)
  expect_equal(totalLength, length(regression.tree@descriptions))
  
  # All descriptions must be non-empty
  for (description in regression.tree@descriptions) {
    expect_false(identical(description, ""))
  }
  
  # First node's description is root node
  expect_equal("Root node", regression.tree@descriptions[1])
  
  expect_equal(0, length(regression.tree@tree_class))
  
  expect_equal(1, regression.tree@tree_number)
  
}

doTest("GBM & DRF tree fetching", test.gbm.trees)