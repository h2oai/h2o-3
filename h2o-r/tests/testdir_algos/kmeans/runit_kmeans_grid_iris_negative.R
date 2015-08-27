setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.kmeans.grid.iris.negative <- function(conn) {
  iris <- h2o.uploadFile(locate("smalldata/iris/iris.csv"))

  ## Invalid kmeans parameters
  grid_space <- list()
  grid_space$max_iterations <- c(1,2,-20)
  grid_space$init = c('Random','PlusPlus','Foo')
  Log.info(lapply(names(grid_space), function(n) paste0("The provided ",n," search space: ", grid_space[n])))

  expected_grid_space <- list()
  expected_grid_space$max_iterations <- c(1,2)
  expected_grid_space$init = c('Random','PlusPlus')
  Log.info(lapply(names(grid_space), function(n) paste0("The expected ",n," search space: ", expected_grid_space[n])))

  Log.info("Constructing the grid of kmeans models with some invalid kmeans parameters...")
  # Skip client check which means that grid will contain failed models.
  iris_kmeans_grid <- h2o.grid("kmeans", grid_id="kmeans_grid_iris_test", x=1:4, k=3, training_frame=iris, hyper_params=grid_space, do_hyper_params_check=FALSE)
  expect_error(h2o.grid("kmeans", grid_id="kmeans_grid_iris_test", x=1:4, k=3, training_frame=iris, hyper_params=grid_space))
  print(iris_kmeans_grid)

  Log.info("Performing various checks of the constructed grid...")
  Log.info("Check cardinality of grid, that is, the correct number of models have been created...")
  expect_equal(length(iris_kmeans_grid@model_ids), 4)

  Log.info("Check that the hyper_params that were passed to grid, were used to construct the models...")
  # Get models
  grid_models <- lapply(iris_kmeans_grid@model_ids, function(mid) { model = h2o.getModel(mid) })
  # Check expected number of models
  expect_equal(length(grid_models), 4)
  # Check parameters coverage
  for ( name in names(grid_space) ) { expect_model_param(grid_models, name, expected_grid_space[[name]]) }

  # TODO: Check error messages for cases with invalid kmeans parameters

  ## Non-gridable parameter passed as grid parameter
  grid_space <- list()
  grid_space$init <- iris[c(2,70,148),1:4]

  Log.info(paste0("Constructing the grid of kmeans models with non-gridable parameter user_points"))
  expect_error(iris_kmeans_grid <- h2o.grid("kmeans", grid_id="kmeans_grid_iris_test", x=1:4, k=3, training_frame=iris, hyper_params=grid_space))

  testEnd()
}

doTest("K-Means Grid Search using bad parameters", check.kmeans.grid.iris.negative)

