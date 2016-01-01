setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.deeplearning.grid.cars.negative <- function() {
  cars <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  seed <- sample(1:1000000, 1)
  h2oTest.logInfo(paste0("runif seed: ",seed))
  r <- h2o.runif(cars,seed=seed)
  train <- cars[r > 0.2,]

  #validation_scheme = sample(1:3,1) # 1:none, 2:cross-validation, 3:validation set
  validation_scheme = 1
  h2oTest.logInfo(paste0("Validation scheme (1:none, 2:cross-validation, 3:validation set): ", validation_scheme))
  if ( validation_scheme == 3 ) { valid <- cars[r <= 0.2,] }
  if ( validation_scheme == 2 ) {
    nfolds = 2
    h2oTest.logInfo(paste0("N-folds: ", nfolds))
  }

  ## Invalid deeplearning parameters
  grid_space <- list()
  grid_space$activation <- sapply(sample(c("Rectifier", "Tanh", "TanhWithDropout", "RectifierWithDropout", "Maxout", "MaxoutWithDropout"), 2), function (x) x)
  grid_space$activation[[3]] <- "Foo"
  grid_space$epochs <- c(1, 2)
  grid_space$distribution <- c(sample(c('bernoulli','multinomial','gaussian'), 1))
  h2oTest.logInfo(lapply(names(grid_space), function(n) paste0("The provided ",n," search space: ", grid_space[n])))

  expected_grid_space <- list()
  expected_grid_space$activation <- grid_space$activation[grid_space$activation != "Foo"]
  expected_grid_space$epochs <- c(1, 2)
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

  h2oTest.logInfo("Constructing the grid of deeplearning models with some invalid deeplearning parameters...")
  if ( validation_scheme == 1 ) {
    cars_deeplearning_grid <- h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                              hyper_params=grid_space, do_hyper_params_check=FALSE)
    expect_error(h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col, training_frame=train,hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else if ( validation_scheme == 2 ) {
    cars_deeplearning_grid <- h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                              nfolds=nfolds, hyper_params=grid_space, do_hyper_params_check=FALSE)
    expect_error(h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col, training_frame=train, nfolds=nfolds, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else {
    cars_deeplearning_grid <- h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                              validation_frame=valid, hyper_params=grid_space, do_hyper_params_check=FALSE)
    expect_error(h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col, training_frame=train, validation_frame=valid, hyper_params=grid_space, do_hyper_params_check=TRUE)) }

  h2oTest.logInfo("Performing various checks of the constructed grid...")
  h2oTest.logInfo("Check cardinality of grid, that is, the correct number of models have been created...")
  expect_equal(length(cars_deeplearning_grid@model_ids), 4)

  h2oTest.logInfo("Check that the hyper_params that were passed to grid, were used to construct the models...")
  # Get models
  grid_models <- lapply(cars_deeplearning_grid@model_ids, function(mid) { model = h2o.getModel(mid) })
  # Check expected number of models
  expect_equal(length(grid_models), 4)
  # Check parameters coverage
  for ( name in names(grid_space) ) { h2oTest.expectModelParam(grid_models, name, expected_grid_space[[name]]) }

  # TODO: Check error messages for cases with invalid deeplearning parameters

  ## Non-gridable parameter passed as grid parameter
  non_gridable_parameter <- sample(1:4, 1)
  if ( non_gridable_parameter == 1 ) { grid_space$export_weights_and_biases <- c(TRUE, FALSE) }
  if ( non_gridable_parameter == 2 ) { grid_space$diagnostics <- c(TRUE, FALSE) }
  if ( non_gridable_parameter == 3 ) { grid_space$momentum_ramp <- c(0.1, 0.5, 0.7) }
  if ( non_gridable_parameter == 4 ) { grid_space$overwrite_with_best_model <- c(TRUE, FALSE) }

  h2oTest.logInfo(paste0("Constructing the grid of deeplearning models with non-gridable parameter: ", non_gridable_parameter ,
                  " (1:balance_classes, 2:r2_stopping, 3:seed). Expecting failure..."))
  if ( validation_scheme == 1 ) {
    expect_error(cars_deeplearning_grid <- h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col,
                                           training_frame=train, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else if ( validation_scheme == 2 ) {
    expect_error(cars_deeplearning_grid <- h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col,
                                           training_frame=train, nfolds=nfolds, hyper_params=grid_space, do_hyper_params_check=TRUE))
  } else {
    expect_error(cars_deeplearning_grid <- h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col,
                                           training_frame=train, validation_frame=valid, hyper_params=grid_space, do_hyper_params_check=TRUE)) }

  
}

h2oTest.doTest("Deep Learning Grid Search using bad parameters", check.deeplearning.grid.cars.negative)
