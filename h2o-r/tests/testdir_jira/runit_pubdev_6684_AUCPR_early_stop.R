setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
######################################################################################
#    This pyunit test is written to ensure that the AUCPR metric can restrict the model training time.
#   See PUBDEV-6684.
######################################################################################
pubdev_6684_test <-
function() {
# random forest
  training1_data <- h2o.importFile(locate("smalldata/junit/cars_20mpg.csv"))
  y_index <- "economy_20mpg"
  x_indices <- c(3:8)
  training1_data["economy_20mpg"] <- as.factor(training1_data["economy_20mpg"])
  print("*************  starting max_runtime_test for Random Forest")
  model <- h2o.randomForest(y=y_index, x=x_indices, training_frame=training1_data, 
                            seed=12345, stopping_rounds=5, stopping_metric="AUCPR", stopping_tolerance=0.1)
  numTreesEarlyStop <- model@model$model_summary$number_of_trees
  print("number of trees built with AUCPR early-stopping is")
  print(numTreesEarlyStop)
  model2 <- h2o.randomForest(y=y_index, x=x_indices, training_frame=training1_data, seed=12345)
  numTrees <- model2@model$model_summary$number_of_trees
  print("number of trees built without AUCPR early-stopping is")
  print(numTrees)
  expect_true(numTrees >= numTreesEarlyStop)
}

doTest("Perform the test for pubdev 6684: use AUCPR as an early stopping metric", pubdev_6684_test)
