setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.kmeans.grid.iris <- function(conn) {
  iris <- h2o.uploadFile(locate("smalldata/iris/iris.csv"))

  grid_space = makeRandomGridSpace(algo="kmeans")
  Log.info(lapply(names(grid_space), function(n) paste0("The ",n," search space: ", grid_space[n])))

  Log.info("Constructing the grid of kmeans models...")
  iris_kmeans_grid = h2o.grid("kmeans", grid_id="kmeans_grid_iris_test", x=1:4, training_frame=iris, hyper_params=grid_space)

  Log.info("Performing various checks of the constructed grid...")
  Log.info("Check cardinality of grid, that is, the correct number of models have been created...")
  size_of_grid_space <- 1
  for ( name in names(grid_space) ) { size_of_grid_space <- size_of_grid_space * length(grid_space[[name]]) }
  expect_equal(length(iris_kmeans_grid@model_ids), size_of_grid_space)

  Log.info("Duplicate-entries-in-grid-space check")
  new_grid_space <- grid_space
  for ( name in names(grid_space) ) { if ( name != "distribution" ) { new_grid_space[[name]] <- c(grid_space[[name]],grid_space[[name]]) } }
  Log.info(lapply(names(new_grid_space), function(n) paste0("The new ",n," search space: ", new_grid_space[n])))
  Log.info("Constructing the new grid of kmeans models...")
  iris_kmeans_grid2 = h2o.grid("kmeans", grid_id="kmeans_grid_iris_test2", x=1:4, training_frame=iris, hyper_params=grid_space)
  expect_equal(length(iris_kmeans_grid@model_ids), length(iris_kmeans_grid2@model_ids))

  # TODO
  # Log.info("Check a random grid model against its equivalent, non-grid model, trained with the same parameters...")
  # Log.info("Check best grid model against a randomly selected grid model...")
  # Log.info("Check negative cases...")

  testEnd()
}

doTest("K-means Grid Search using iris dataset", check.kmeans.grid.iris)

