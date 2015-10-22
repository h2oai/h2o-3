setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

check.gbm.grid.cars <- function() {
  cars <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
  seed <- sample(1:1000000, 1)
  Log.info(paste0("runif seed: ",seed))
  r <- h2o.runif(cars,seed=seed)
  train <- cars[r > 0.2,]

  validation_scheme = sample(1:3,1) # 1:none, 2:cross-validation, 3:validation set
  Log.info(paste0("Validation scheme (1:none, 2:cross-validation, 3:validation set): ", validation_scheme))
  if ( validation_scheme == 3 ) { valid <- cars[r <= 0.2,] }
  if ( validation_scheme == 2 ) {
    nfolds = 2
    Log.info(paste0("N-folds: ", nfolds))
  }

  grid_space = makeRandomGridSpace(algo="gbm")
  Log.info(lapply(names(grid_space), function(n) paste0("The ",n," search space: ", grid_space[n])))

  predictors <- c("displacement","power","weight","acceleration","year")
  if        ( grid_space$distribution == 'bernoulli' )  { response_col <- "economy_20mpg"
  } else if ( grid_space$distribution == 'gaussian')    { response_col <- "economy"
  } else                                                { response_col <- "cylinders" }
  Log.info(paste0("Predictors: ", paste(predictors, collapse=',')))
  Log.info(paste0("Response: ", response_col))

  if ( grid_space$distribution %in% c('bernoulli', 'multinomial') ) {
    Log.info("Converting the response column to a factor...")
    train[,response_col] <- as.factor(train[,response_col])
    if ( validation_scheme == 3 ) { valid[,response_col] <- as.factor(valid[,response_col]) } }

  Log.info("Constructing the grid of gbm models...")
  if ( validation_scheme == 1 ) {
    cars_gbm_grid = h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                             hyper_params=grid_space)
  } else if ( validation_scheme == 2 ) {
    cars_gbm_grid = h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                             nfolds=nfolds, hyper_params=grid_space)
  } else {
    cars_gbm_grid = h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                             validation_frame=valid, hyper_params=grid_space) }

  Log.info("Performing various checks of the constructed grid...")
  Log.info("Check cardinality of grid, that is, the correct number of models have been created...")
  size_of_grid_space <- 1
  for ( name in names(grid_space) ) { size_of_grid_space <- size_of_grid_space * length(grid_space[[name]]) }
  expect_equal(length(cars_gbm_grid@model_ids), size_of_grid_space)

  Log.info("Duplicate-entries-in-grid-space check")
  new_grid_space <- grid_space
  for ( name in names(grid_space) ) { if ( name != "distribution" ) { new_grid_space[[name]] <- c(grid_space[[name]],grid_space[[name]]) } }
  Log.info(lapply(names(new_grid_space), function(n) paste0("The new ",n," search space: ", new_grid_space[n])))
  Log.info("Constructing the new grid of gbm models...")
  if ( validation_scheme == 1 ) {
    cars_gbm_grid2 = h2o.grid("gbm", grid_id="gbm_grid_cars_test2", x=predictors, y=response_col, training_frame=train,
                             hyper_params=new_grid_space)
  } else if ( validation_scheme == 2 ) {
    cars_gbm_grid2 = h2o.grid("gbm", grid_id="gbm_grid_cars_test2", x=predictors, y=response_col, training_frame=train,
                             nfolds=nfolds, hyper_params=new_grid_space)
  } else {
    cars_gbm_grid2 = h2o.grid("gbm", grid_id="gbm_grid_cars_test2", x=predictors, y=response_col, training_frame=train,
                             validation_frame=valid, hyper_params=new_grid_space) }
  expect_equal(length(cars_gbm_grid@model_ids), length(cars_gbm_grid2@model_ids))

  Log.info("Check that the hyper_params that were passed to grid, were used to construct the models...")
  # Get models
  grid_models <- lapply(cars_gbm_grid@model_ids, function(mid) { model = h2o.getModel(mid) })
  # Check expected number of models
  expect_equal(length(grid_models), size_of_grid_space)
  # Check parameters coverage
  for ( name in names(grid_space) ) { expect_model_param(grid_models, name, grid_space[[name]]) }

  # TODO
  # Log.info("Check best grid model against a randomly selected grid model...")

  
}

doTest("GBM Grid Search using cars dataset", check.gbm.grid.cars)

