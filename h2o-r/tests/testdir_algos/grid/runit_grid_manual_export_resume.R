setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.grid.resume <- function() {
  iris.hex <- h2o.importFile(path = locate("smalldata/iris/iris.csv"), destination_frame="iris.hex")

  ntrees_opts <- c(1, 5)
  learn_rate_opts <- c(0.1, 0.01)
  
  hyper_parameters <- list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
  baseline_grid <- h2o.grid("gbm", grid_id="gbm_grid_test", x=1:4, y=5, training_frame=iris.hex, hyper_params = hyper_parameters)
  grid_id <- baseline_grid@grid_id
  saved_path <- h2o.saveGrid(grid_directory = tempdir(), grid_id = grid_id)
  baseline_model_count <- length(baseline_grid@model_ids)
  print(baseline_grid@model_ids)
  
  # Wipe the cloud to simulate cluster restart - the models will no longer be available
  h2o.removeAll()
  
  # Load the Grid back in with all the models checkpointed
  grid <- h2o.loadGrid(saved_path)
  expect_true(length(grid@model_ids) == baseline_model_count)
  
  # Load the dataset in once again, as it was removed as the cloud was wiped.
  iris.hex <- h2o.uploadFile(locate("smalldata/iris/iris.csv"), destination_frame="iris.hex")
  #Start the grid search once again, should contain the original models and more
  grid <- h2o.grid("gbm", grid_id="gbm_grid_test", x=1:4, y=5, training_frame=iris.hex, hyper_params = hyper_parameters)
  expect_true(length(grid@model_ids) == baseline_model_count)
  print(grid@model_ids)
  
  # Check all the models for availability
  for(model_id in grid@model_ids){
    model <- h2o.getModel(model_id = model_id)
    expect_false(is.null(model))
  }

  # test again this time saving the grid with frames
  saved_path2 <- h2o.saveGrid(grid_directory = tempdir(), grid_id = grid_id, save_params_references = TRUE)
  h2o.removeAll()
  h2o.loadGrid(saved_path2, load_params_references = TRUE)
  hyper_parameters[["ntrees"]] <- c(2, 3)
  grid <- h2o.grid("gbm", grid_id="gbm_grid_test", x=1:4, y=5, training_frame=iris.hex, hyper_params = hyper_parameters)
  expect_true(length(grid@model_ids) == baseline_model_count+4)
  print(grid@model_ids)
}

doTest("Resume grid search after cluster restart", test.grid.resume)
