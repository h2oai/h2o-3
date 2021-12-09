setwd(normalizePath(dirname(
  R.utils::commandArgs(asValues = TRUE)$"f"
)))
source("../../../scripts/h2o-r-test-setup.R")



test.grid.isofor <- function() {
  iris.hex <-
    h2o.importFile(path = locate("smalldata/iris/iris.csv"),
                   destination_frame = "iris.hex")
  
  ntrees_opts = c(5, 10)
  size_of_hyper_space = length(ntrees_opts)
  
  hyper_parameters = list(ntrees = ntrees_opts)
  baseline_grid <-
    h2o.grid(
      "isolationforest",
      grid_id = "isofor_grid_test",
      x = 1:4,
      training_frame = iris.hex,
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

  # introduce "some" label column - in this test we only check that the grid runs
  model <-
    h2o.isolationForest(training_frame = train,
                        seed = 1234,
                        x = 1:4,
                        ntrees = max(ntrees_opts),
                        sample_size = 5)
  predictions <- h2o.predict(model, test)
  threshold <-
    h2o.quantile(probs = c(0.8), x = predictions)["predictQuantiles"]
  print(threshold)
  labels_test <- predictions > threshold
  test["label"] = h2o.asfactor(labels_test["predict"])

  standard_grid <-
    h2o.grid(
      "isolationforest",
      grid_id = "isofor_grid_test_standard",
      x = 1:4,
      training_frame = train,
      hyper_params = hyper_parameters,
      sample_size = 5,
      seed = 42
  )
  summary(standard_grid, show_stack_traces = TRUE)
  
  validated_grid <-
    h2o.grid(
      "isolationforest",
      grid_id = "isofor_grid_test_validation",
      x = 1:4,
      training_frame = train,
      validation_frame = test,
      hyper_params = hyper_parameters,
      validation_response_column = "label",
      sample_size = 5,
      seed = 42
    )
    summary(validated_grid, show_stack_traces = TRUE)

    # check we have the same models regardless of using validation frame
    expect_equal(length(standard_grid@model_ids), length(validated_grid@model_ids))
    for (i in 1:length(standard_grid@model_ids)) {
      standard <- h2o.getModel(standard_grid@model_ids[[i]])
      validated <- h2o.getModel(validated_grid@model_ids[[i]])
      expect_equal(standard@model$training_metrics@metrics$mean_score, validated@model$training_metrics@metrics$mean_score)
      expect_true(h2o.auc(validated, valid=TRUE) > 0.65)
    }
}

doTest("Isolation Forest Grid Search test", test.grid.isofor)
