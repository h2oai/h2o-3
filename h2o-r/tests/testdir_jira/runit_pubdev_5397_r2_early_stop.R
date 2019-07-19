setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################################
#    This pyunit test is written to ensure that the r2 metric can restrict the model training time.
#   See PUBDEV-5397.
######################################################################################
pubdev_5397_test <-
function() {
# random forest
  training1_data <- h2o.importFile(locate("smalldata/gridsearch/multinomial_training1_set.csv"))
  y_index <- h2o.ncol(training1_data)
  x_indices <- c(1:(y_index-1))
  training1_data["C14"] <- as.factor(training1_data["C14"])
  print("*************  starting max_runtime_test for Random Forest")
  model <- h2o.randomForest(y=y_index, x=x_indices, training_frame=training1_data, seed=12345, stopping_rounds=5, stopping_metric="r2", stopping_tolerance=0.01)
  numTreesEarlyStop <- model@model$model_summary$number_of_trees
  print("number of trees built with r2 early-stopping is")
  print(numTreesEarlyStop)
  model2 <- h2o.randomForest(y=y_index, x=x_indices, training_frame=training1_data, seed=12345)
  numTrees <- model2@model$model_summary$number_of_trees
  print("number of trees built without r2 early-stopping is")
  print(numTrees)
  expect_true(numTrees >= numTreesEarlyStop)
}

doTest("Perform the test for pubdev 5397 r2 early stopping", pubdev_5397_test)
