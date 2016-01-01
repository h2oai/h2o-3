setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.kmeans.grid.iris <- function() {
  iris <- h2o.uploadFile(h2oTest.locate("smalldata/iris/iris.csv"))

  grid_space = h2oTest.makeRandomGridSpace(algo="kmeans")
  h2oTest.logInfo(lapply(names(grid_space), function(n) paste0("The ",n," search space: ", grid_space[n])))

  h2oTest.logInfo("Constructing the grid of kmeans models...")
  iris_kmeans_grid = h2o.grid("kmeans", grid_id="kmeans_grid_iris_test", x=1:4, training_frame=iris, hyper_params=grid_space)

  h2oTest.logInfo("Performing various checks of the constructed grid...")
  h2oTest.logInfo("Check cardinality of grid, that is, the correct number of models have been created...")
  size_of_grid_space <- 1
  for ( name in names(grid_space) ) { size_of_grid_space <- size_of_grid_space * length(grid_space[[name]]) }
  expect_equal(length(iris_kmeans_grid@model_ids), size_of_grid_space)

  h2oTest.logInfo("Duplicate-entries-in-grid-space check")
  new_grid_space <- grid_space
  for ( name in names(grid_space) ) { new_grid_space[[name]] <- c(grid_space[[name]],grid_space[[name]]) }
  h2oTest.logInfo(lapply(names(new_grid_space), function(n) paste0("The new ",n," search space: ", new_grid_space[n])))
  h2oTest.logInfo("Constructing the new grid of kmeans models...")
  iris_kmeans_grid2 = h2o.grid("kmeans", grid_id="kmeans_grid_iris_test2", x=1:4, training_frame=iris, hyper_params=grid_space)
  expect_equal(length(iris_kmeans_grid@model_ids), length(iris_kmeans_grid2@model_ids))

  h2oTest.logInfo("Check that the hyper_params that were passed to grid, were used to construct the models...")
  # Get models
  grid_models <- lapply(iris_kmeans_grid2@model_ids, function(mid) { model = h2o.getModel(mid) })
  # Check expected number of models
  expect_equal(length(grid_models), size_of_grid_space)
  # Check parameters coverage
  for ( name in names(grid_space) ) { h2oTest.expectModelParam(grid_models, name, grid_space[[name]]) }

  # TODO
  # h2oTest.logInfo("Check best grid model against a randomly selected grid model...")

  
}

h2oTest.doTest("K-means Grid Search using iris dataset", check.kmeans.grid.iris)

