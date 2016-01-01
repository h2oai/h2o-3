setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.gbm.grid.cars.negative <- function() {
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

  ## Invalid gbm parameters
  grid_space <- list()
  grid_space$ntrees <- c(5,10,-5)
  grid_space$max_depth <- c(2,5)
  grid_space$min_rows <- c(1,10,-7)
  grid_space$distribution <- sample(c('bernoulli','multinomial','gaussian','poisson','tweedie','gamma'), 1)
  h2oTest.logInfo(lapply(names(grid_space), function(n) paste0("The provided ",n," search space: ", grid_space[n])))

  expected_grid_space <- list()
  expected_grid_space$ntrees <- c(5,10)
  expected_grid_space$max_depth <- c(2,5)
  expected_grid_space$min_rows <- c(1,10)
  expected_grid_space$distribution <- grid_space$distribution
  h2oTest.logInfo(lapply(names(grid_space), function(n) paste0("The expected ",n," search space: ", expected_grid_space[n])))

  predictors <- c("displacement","power","weight","acceleration","year")
  if        ( grid_space$distribution == 'bernoulli' )  { response_col <- "economy_20mpg"
  } else if ( grid_space$distribution == 'gaussian')    { response_col <- "economy"
  } else                                                { response_col <- "cylinders" }
  h2oTest.logInfo(paste0("Predictors: ", paste(predictors, collapse=',')))
  h2oTest.logInfo(paste0("Response: ", response_col))

  if ( grid_space$distribution %in% c('bernoulli', 'multinomial') ) {
    h2oTest.logInfo("Converting the response column to a factor...")
    train[,response_col] <- as.factor(train[,response_col])
    if ( validation_scheme == 3 ) { valid[,response_col] <- as.factor(valid[,response_col]) } }

  h2oTest.logInfo("Constructing the grid of gbm models with some invalid gbm parameters...")
  if ( validation_scheme == 1 ) {
    cars_gbm_grid <- h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train, hyper_params=grid_space, do_hyper_params_check=FALSE)
    expect_error(h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else if ( validation_scheme == 2 ) {
    cars_gbm_grid <- h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train, nfolds=nfolds, hyper_params=grid_space, do_hyper_params_check=FALSE)
    expect_error(h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train, nfolds=nfolds, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else {
    cars_gbm_grid <- h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train, validation_frame=valid, hyper_params=grid_space, do_hyper_params_check=FALSE)
    expect_error(h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train, validation_frame=valid, hyper_params=grid_space, do_hyper_params_check=TRUE)) }

  h2oTest.logInfo("Performing various checks of the constructed grid...")
  h2oTest.logInfo("Check cardinality of grid, that is, the correct number of models have been created...")
  expect_equal(length(cars_gbm_grid@model_ids), 8)

  h2oTest.logInfo("Check that the hyper_params that were passed to grid, were used to construct the models...")
  # Get models
  grid_models <- lapply(cars_gbm_grid@model_ids, function(mid) { model = h2o.getModel(mid) })
  # Check expected number of models
  expect_equal(length(grid_models), 8)
  # Check parameters coverage
  for ( name in names(grid_space) ) { h2oTest.expectModelParam(grid_models, name, expected_grid_space[[name]]) }

  # TODO: Check error messages for cases with invalid gbm parameters

  ## Non-gridable parameter passed as grid parameter
  grid_space$build_tree_one_node <- c(TRUE, FALSE)

  h2oTest.logInfo(paste0("Constructing the grid of gbm models with non-gridable parameter build_tree_one_node"))
  if ( validation_scheme == 1 ) {
    expect_error(cars_gbm_grid <- h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col,
                                           training_frame=train, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else if ( validation_scheme == 2 ) {
    expect_error(cars_gbm_grid <- h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col,
                                           training_frame=train, nfolds=nfolds, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else {
    expect_error(cars_gbm_grid <- h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col,
                                           training_frame=train, validation_frame=valid, hyper_params=grid_space, do_hyper_params_check=TRUE)) }

  
}

h2oTest.doTest("GBM Grid Search using bad parameters", check.gbm.grid.cars.negative)

