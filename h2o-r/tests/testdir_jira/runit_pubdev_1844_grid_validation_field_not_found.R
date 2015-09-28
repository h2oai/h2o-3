setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev.1844.field.not.found <- function(conn) {
  cars <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
  seed <- 919927
  Log.info(paste0("runif seed: ",seed))
  r <- h2o.runif(cars,seed=seed)
  train <- cars[r > 0.2,]
  valid <- cars[r <= 0.2,]

  grid_space <- list()
  grid_space$nbins <- c(7)
  #grid_space$nbins_cats <- c(22)
  #grid_space$ntrees <- c(7)
  grid_space$distribution <- "gaussian"
  Log.info(lapply(names(grid_space), function(n) paste0("The ",n," search space: ", grid_space[n])))

  predictors <- c("displacement","power","weight","acceleration","year")
  response_col <- "economy"
  Log.info(paste0("Predictors: ", paste(predictors, collapse=',')))
  Log.info(paste0("Response: ", response_col))

  Log.info("Constructing the grid of gbm models...")
  cars_gbm_grid = h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                           validation_frame=valid, hyper_params=grid_space)

  Log.info("Check cardinality of grid, that is, the correct number of models have been created...")
  size_of_grid_space <- 1
  for ( name in names(grid_space) ) { size_of_grid_space <- size_of_grid_space * length(grid_space[[name]]) }
  Log.info(paste0("The grid should be of size: ",size_of_grid_space))
  Log.info(paste0("The actual grid size: ", length(cars_gbm_grid@model_ids)))
  expect_equal(length(cars_gbm_grid@model_ids), size_of_grid_space)

  Log.info("There shouldn't be any failures, but here they are, just in case...")
  Log.info("failure_details...")
  Log.info(cars_gbm_grid@failure_details)
  Log.info("failed_params...")
  Log.info(cars_gbm_grid@failed_params)
  Log.info("failed_raw_params...")
  Log.info(cars_gbm_grid@failed_raw_params)

  
}

doTest("PUBDEV-1844", test.pubdev.1844.field.not.found)
