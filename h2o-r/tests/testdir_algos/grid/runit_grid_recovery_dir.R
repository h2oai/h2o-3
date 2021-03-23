setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.grid.resume <- function() {
  iris.hex <- h2o.importFile(path = locate("smalldata/iris/iris.csv"), destination_frame="iris.hex")

  ntrees_opts <- c(100, 200)
  learn_rate_opts <- c(0.01, 0.02)
  export_dir <- tempdir()

  hyper_parameters <- list(ntrees = ntrees_opts, learn_rate = learn_rate_opts)
  baseline_grid <- h2o.grid(
      "gbm", grid_id="grid_ft_resume_test", 
      x=1:4, y=5, training_frame=iris.hex, 
      hyper_params=hyper_parameters,
      recovery_dir=export_dir
  )
  grid_id <- baseline_grid@grid_id
  baseline_model_count <- length(baseline_grid@model_ids)
  print(baseline_grid@model_ids)
  
  # check the recovery dir is empty after grid success
  print(list.files(export_dir))
  expect_true(length(list.files(export_dir)) == 0)
  grid_path <- h2o.saveGrid(export_dir, grid_id, save_params_references=TRUE)
  h2o.removeAll()

  grid <- h2o.loadGrid(grid_path, load_params_references = TRUE)
  h2o.resumeGrid(grid@grid_id)
  # no new models were trained
  expect_equal(baseline_model_count, length(grid@model_ids))
}

doTest("Resume grid search after cluster restart", test.grid.resume)
