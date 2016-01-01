setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.GBM.checkpoint_on_iris <- function() {
  train.hex <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris_train.csv"), "train.hex")
  test.hex <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris_test.csv"), "test.hex")

  # Number of trees for model building
  ntrees.initial <- 2
  ntrees.cont <- ntrees.initial + 4
  ntrees.total <- ntrees.cont

  ##
  ## Test for classification
  ##
  # Build an initial GBM model
  iris.gbm.initial <- h2o.gbm(y = 5, x = 1:4, training_frame = train.hex,
                               validation_frame = test.hex,
                               ntrees = ntrees.initial)
  # Continue building the initial model by appending more trees
  iris.gbm.cont <- h2o.gbm(y = 5, x = 1:4, training_frame = train.hex,
                               validation_frame = test.hex,
                               checkpoint = iris.gbm.initial@parameters$model_id,
                               ntrees = ntrees.cont)
  # Build the same model from the scratch
  iris.gbm.total <- h2o.gbm(y = 5, x = 1:4, training_frame = train.hex,
                                 validation_frame = test.hex,
                                 ntrees = ntrees.total)

  # Verify that model built from checkpoint and model built from scratch are same
  expect_equal(iris.gbm.cont@parameters$ntrees, iris.gbm.total@parameters$ntrees)
  # Training metrics should be same
  # FIXME this is not implemented on backend (we do not properly reconstruct state for OOB recomputation)
  a <- iris.gbm.cont@model$traininig_metrics
  b <- iris.gbm.total@model$training_metrics
  # FIXME PLEASE PLEASE PLEASE expect_mm_equal(a, b)

  # Validation metrics should be same
  a <- iris.gbm.cont@model$validation_metrics
  b <- iris.gbm.total@model$validation_metrics
  expect_mm_equal(a, b)

  
}

expect_mm_equal <- function(a, b, msg) {
  cmA <- a@metrics$cm$table
  cmB <- b@metrics$cm$table
  expect_equal(cmA, cmB)
  expect_equal(a@metrics$model_category, b@metrics$model_category)
  expect_equal(a@metrics$MSE, b@metrics$MSE)
  expect_equal(a@metrics$r2, b@metrics$r2)
  expect_equal(a@metrics$hit_ratio_table$hit_ratio, b@metrics$hit_ratio_table$hit_ratio)
  expect_equal(a@metrics$logloss, b@metrics$logloss)
}

h2oTest.doTest("GBM test checkpoint on iris", test.GBM.checkpoint_on_iris)
