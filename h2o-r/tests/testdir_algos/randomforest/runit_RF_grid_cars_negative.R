setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.drf.grid.cars.negative <- function() {
  cars <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  seed <- sample(1:1000000, 1)
  h2oTest.logInfo(paste0("runif seed: ",seed))
  r <- h2o.runif(cars,seed=seed)
  train <- cars[r > 0.2,]

  validation_scheme = sample(1:3,1) # 1:none, 2:cross-validation, 3:validation set
  h2oTest.logInfo(paste0("Validation scheme (1:none, 2:cross-validation, 3:validation set): ", validation_scheme))
  if ( validation_scheme == 3 ) { valid <- cars[r <= 0.2,] }
  if ( validation_scheme == 2 ) {
    nfolds = 2
    h2oTest.logInfo(paste0("N-folds: ", nfolds))
  }

  problem = sample(0:2,1)
  h2oTest.logInfo(paste0("Type model-building exercise (0:regression, 1:binomial, 2:multinomial): ", problem))

  predictors <- c("displacement","power","weight","acceleration","year")
  if        ( problem == 1 ) { response_col <- "economy_20mpg"
  } else if ( problem == 2 ) { response_col <- "cylinders"
  } else                     { response_col <- "economy" }

  if ( problem == 1 || problem == 2 ) {
    h2oTest.logInfo("Converting the response column to a factor...")
    train[,response_col] <- as.factor(train[,response_col])
    if ( validation_scheme == 3 ) { valid[,response_col] <- as.factor(valid[,response_col]) } }

  h2oTest.logInfo(paste0("Predictors: ", paste(predictors, collapse=',')))
  h2oTest.logInfo(paste0("Response: ", response_col))

  ## Invalid drf parameters
  grid_space <- list()
  grid_space$ntrees <- c(5,10,-5)
  grid_space$max_depth <- c(2,5)
  grid_space$min_rows <- c(1,10,-7)
  h2oTest.logInfo(lapply(names(grid_space), function(n) paste0("The provided ",n," search space: ", grid_space[n])))

  expected_grid_space <- list()
  expected_grid_space$ntrees <- c(5,10)
  expected_grid_space$max_depth <- c(2,5)
  expected_grid_space$min_rows <- c(1,10)
  h2oTest.logInfo(lapply(names(grid_space), function(n) paste0("The expected ",n," search space: ", expected_grid_space[n])))

  h2oTest.logInfo("Constructing the grid of drf models with some invalid drf parameters...")
  if ( validation_scheme == 1 ) {
    cars_drf_grid <- h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col, training_frame=train, hyper_params=grid_space, do_hyper_params_check=FALSE)
    expect_error(h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col, training_frame=train, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else if ( validation_scheme == 2 ) {
    cars_drf_grid <- h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col, training_frame=train, nfolds=nfolds, hyper_params=grid_space, do_hyper_params_check=FALSE)
    expect_error(h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col, training_frame=train, nfolds=nfolds, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else {
    cars_drf_grid <- h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col, training_frame=train, validation_frame=valid, hyper_params=grid_space, do_hyper_params_check=FALSE)
    expect_error(h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col, training_frame=train, validation_frame=valid, hyper_params=grid_space, do_hyper_params_check=TRUE)) }

  h2oTest.logInfo("Performing various checks of the constructed grid...")
  h2oTest.logInfo("Check cardinality of grid, that is, the correct number of models have been created...")
  expect_equal(length(cars_drf_grid@model_ids), 8)

  h2oTest.logInfo("Check that the hyper_params that were passed to grid, were used to construct the models...")
  # Get models
  grid_models <- lapply(cars_drf_grid@model_ids, function(mid) { model = h2o.getModel(mid) })
  # Check expected number of models
  expect_equal(length(grid_models), 8)
  # Check parameters coverage
  for ( name in names(grid_space) ) { h2oTest.expectModelParam(grid_models, name, expected_grid_space[[name]]) }

  # TODO: Check error messages for cases with invalid drf parameters

  ## Non-gridable parameter passed as grid parameter
  non_gridable_parameter <- sample(1:2, 1)
  if ( non_gridable_parameter == 1 ) { grid_space$build_tree_one_node <- c(TRUE, FALSE) }
  if ( non_gridable_parameter == 2 ) { grid_space$binomial_double_trees <- c(TRUE, FALSE) }

  h2oTest.logInfo(paste0("Constructing the grid of drf models with non-gridable parameter: ", non_gridable_parameter ,
                  " (1:build_tree_one_node, 2:binomial_double_trees). Expecting failure..."))
  if ( validation_scheme == 1 ) {
    expect_error(cars_drf_grid <- h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col,
                                           training_frame=train, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else if ( validation_scheme == 2 ) {
    expect_error(cars_drf_grid <- h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col,
                                           training_frame=train, nfolds=nfolds, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else {
    expect_error(cars_drf_grid <- h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col,
                                           training_frame=train, validation_frame=valid, hyper_params=grid_space, do_hyper_params_check=TRUE)) }

  
}

h2oTest.doTest("Random Forest Grid Search using bad parameters", check.drf.grid.cars.negative)

