setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.naivebayes.grid.cars.negative <- function(conn) {
  train <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))

  ## Invalid naivebayes parameters
  grid_space <- list()
  grid_space$laplace <- c(1,2,-5)
  grid_space$min_sdev <- c(0.1,0.2,-60)
  grid_space$eps_sdev <- c(0.5,0.6,-60)
  Log.info(lapply(names(grid_space), function(n) paste0("The provided ",n," search space: ", grid_space[n])))

  expected_grid_space <- list()
  expected_grid_space$laplace <- c(1,2)
  expected_grid_space$min_sdev <- c(0.1,0.2)
  expected_grid_space$eps_sdev <- c(0.5,0.6)
  Log.info(lapply(names(grid_space), function(n) paste0("The expected ",n," search space: ", expected_grid_space[n])))

  problem <- sample(1:2,1)
  Log.info(paste0("Type model-building exercise (1:binomial, 2:multinomial): ", problem))

  predictors <- c("displacement","power","weight","acceleration","year")
  if        ( problem == 1 ) { response_col <- "economy_20mpg"
  } else                     { response_col <- "cylinders" }
  Log.info(paste0("Predictors: ", paste(predictors, collapse=',')))
  Log.info(paste0("Response: ", response_col))

  Log.info("Converting the response column to a factor...")
  train[,response_col] <- as.factor(train[,response_col])

  Log.info("Constructing the grid of naivebayes models with some invalid naivebayes parameters...")
  cars_naivebayes_grid <- h2o.grid("naivebayes", grid_id="naiveBayes_grid_cars_test", x=predictors, y=response_col,
                                   training_frame=train, hyper_params=grid_space)

  Log.info("Performing various checks of the constructed grid...")
  Log.info("Check cardinality of grid, that is, the correct number of models have been created...")
  expect_equal(length(cars_naivebayes_grid@model_ids), 8)

  Log.info("Check that the hyper_params that were passed to grid, were used to construct the models...")
  # Get models
  grid_models <- lapply(cars_naivebayes_grid@model_ids, function(mid) { model = h2o.getModel(mid) })
  # Check expected number of models
  expect_equal(length(grid_models), 8)
  # Check parameters coverage
  for ( name in names(grid_space) ) { expect_model_param(grid_models, name, expected_grid_space[[name]]) }

  # TODO: Check error messages for cases with invalid naivebayes parameters

  ## Non-gridable parameter passed as grid parameter
  grid_space$class_sampling_factors <- c(TRUE, FALSE)

  Log.info(paste0("Constructing the grid of naivebayes models with non-gridable parameter class_sampling_factors"))
  expect_error(cars_naivebayes_grid <- h2o.grid("naivebayes", grid_id="naivebayes_grid_cars_test", x=predictors, y=response_col,
                                                training_frame=train, hyper_params=grid_space))

  testEnd()
}

doTest("Naive Bayes Grid Search using bad parameters", check.naivebayes.grid.cars.negative)

