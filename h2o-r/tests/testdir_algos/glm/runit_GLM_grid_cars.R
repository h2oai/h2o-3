setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



check.glm.grid.cars <- function(conn) {
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

  grid_space = h2oTest.makeRandomGridSpace(algo="glm")
  h2oTest.logInfo(lapply(names(grid_space), function(n) paste0("The ",n," search space: ", grid_space[n])))

  predictors <- c("displacement","power","weight","acceleration","year")
  if        ( grid_space$family == 'binomial' )  { response_col <- "economy_20mpg"
  } else if ( grid_space$family == 'gaussian')    { response_col <- "economy"
  } else                                          { response_col <- "cylinders" }
  h2oTest.logInfo(paste0("Predictors: ", paste(predictors, collapse=',')))
  h2oTest.logInfo(paste0("Response: ", response_col))

  if ( grid_space$family == 'binomial' ) {
    h2oTest.logInfo("Converting the response column to a factor...")
    train[,response_col] <- as.factor(train[,response_col])
    if ( validation_scheme == 3 ) { valid[,response_col] <- as.factor(valid[,response_col]) } }

  h2oTest.logInfo("Constructing the grid of glm models...")
  family <- grid_space$family
  grid_space$family <- NULL
  if ( validation_scheme == 1 ) {
    cars_glm_grid = h2o.grid("glm", grid_id="glm_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                             family=family, hyper_params=grid_space)
  } else if ( validation_scheme == 2 ) {
    cars_glm_grid = h2o.grid("glm", grid_id="glm_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                             family=family, nfolds=nfolds, hyper_params=grid_space)
  } else {
    cars_glm_grid = h2o.grid("glm", grid_id="glm_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                             family=family, validation_frame=valid, hyper_params=grid_space) }

  h2oTest.logInfo("Performing various checks of the constructed grid...")
  h2oTest.logInfo("Check cardinality of grid, that is, the correct number of models have been created...")
  size_of_grid_space <- 1
  for ( name in names(grid_space) ) { size_of_grid_space <- size_of_grid_space * length(grid_space[[name]]) }
  expect_equal(length(cars_glm_grid@model_ids), size_of_grid_space)

  h2oTest.logInfo("Duplicate-entries-in-grid-space check")
  new_grid_space <- grid_space
  for ( name in names(grid_space) ) { new_grid_space[[name]] <- c(grid_space[[name]],grid_space[[name]]) }
  h2oTest.logInfo(lapply(names(new_grid_space), function(n) paste0("The new ",n," search space: ", new_grid_space[n])))
  h2oTest.logInfo("Constructing the new grid of glm models...")
  if ( validation_scheme == 1 ) {
    cars_glm_grid2 = h2o.grid("glm", grid_id="glm_grid_cars_test2", x=predictors, y=response_col, training_frame=train,
                              family=family, hyper_params=new_grid_space)
  } else if ( validation_scheme == 2 ) {
    cars_glm_grid2 = h2o.grid("glm", grid_id="glm_grid_cars_test2", x=predictors, y=response_col, training_frame=train,
                              family=family, nfolds=nfolds, hyper_params=new_grid_space)
  } else {
    cars_glm_grid2 = h2o.grid("glm", grid_id="glm_grid_cars_test2", x=predictors, y=response_col, training_frame=train,
                              family=family, validation_frame=valid, hyper_params=new_grid_space) }
  expect_equal(length(cars_glm_grid@model_ids), length(cars_glm_grid2@model_ids))

  h2oTest.logInfo("Check that the hyper_params that were passed to grid, were used to construct the models...")
  # Get models
  grid_models <- lapply(cars_glm_grid@model_ids, function(mid) { model = h2o.getModel(mid) })
  # Check expected number of models
  expect_equal(length(grid_models), size_of_grid_space)
  # Check parameters coverage
  for ( name in names(grid_space) ) { h2oTest.expectModelParam(grid_models, name, grid_space[[name]]) }

  # TODO
  # h2oTest.logInfo("Check best grid model against a randomly selected grid model...")

  
}

h2oTest.doTest("GLM Grid Search using cars dataset", check.glm.grid.cars)

