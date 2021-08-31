setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../../scripts/h2o-r-test-setup.R")



test.grid.resume <- function() {
  iris.hex <-
    h2o.importFile(path = locate("smalldata/iris/iris.csv"),
                   destination_frame = "iris.hex")
  
  ntrees_opts = c(1, 5)
  size_of_hyper_space = length(ntrees_opts)
  
  hyper_parameters = list(ntrees = ntrees_opts)
  baseline_grid <-
    h2o.grid(
      "isolationforest",
      grid_id = "isofor_grid_test",
      x = 1:4,
      y = 5,
      training_frame = iris.hex,
      is_supervised = TRUE,
      hyper_params = hyper_parameters
    )
  grid_id <- baseline_grid@grid_id
  expect_equal(length(baseline_grid@model_ids),
               length(ntrees_opts))
  
  # Grid search with validation frame and validation_response_column
  train <-
    h2o.importFile(locate("smalldata/anomaly/ecg_discord_train.csv"))
  test <-
    h2o.importFile(locate("smalldata/anomaly/ecg_discord_test.csv"))
  
  model <-
    h2o.isolationForest(training_frame = train,
                        seed = 1234,
                        ntrees = 10)
  predictions <- h2o::h2o.predict(model, test)
  threshold <-
    h2o.quantile(probs = c(0.8), x = predictions)["predictQuantiles"]
  print(threshold)
  labels_test <- predictions > threshold
  test["label"] = h2o.asfactor(labels_test["predict"])
  
  validated_grid <-
    h2o.grid(
      "isolationforest",
      grid_id = "isofor_grid_test_validation",
      x = 1:4,
      y = 5,
      training_frame = train,
      validation_frame = test,
      is_supervised = TRUE,
      hyper_params = hyper_parameters,
      validation_response_column = "label"
    )
}

doTest("Parallel Grid Search test", test.grid.resume)
