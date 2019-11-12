setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

check.glm.grid.checkpoints <- function(conn) {
  cars <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
  train <- cars

  hyper_params = list()
  hyper_params$alpha <- c(0, 0.5, 1)
  predictors <- c("displacement","power","weight","acceleration","year")
  checkpoints_dir = tempfile()

  grid = h2o.grid("glm", grid_id="glm_grid_cars_test", x=predictors, y="economy", training_frame=train,
                           family="gaussian", hyper_params=hyper_params, export_checkpoints_dir=checkpoints_dir)

  saved_models <- list.files(checkpoints_dir)
  num_files <- length(saved_models)
  unlink(checkpoints_dir, recursive = TRUE)
  
  print(saved_models)
  print(grid@model_ids)
  
  expect_equal(num_files, 3)
  expect_equal(num_files, length(grid@model_ids))
}

doTest("GLM Grid Search with saving checkpoints", check.glm.grid.checkpoints)
