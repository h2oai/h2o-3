setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev_1844 <- function() {
  cars <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
  seed <- sample(1:1000000, 1)
  Log.info(paste0("runif seed: ",seed))
  r <- h2o.runif(cars,seed=seed)
  train <- cars[r > 0.2,]
  valid <- cars[r <= 0.2,]

  grid_space <- list()
  grid_space$min_rows <- c(2,4)
  grid_space$nbins_cats <- c(269, 74)
  grid_space$distribution <- "poisson"
  Log.info(lapply(names(grid_space), function(n) paste0("The ",n," search space: ", grid_space[n])))

  predictors <- c("displacement","power","weight","acceleration","year")
  response_col <- "cylinders"
  Log.info(paste0("Predictors: ", paste(predictors, collapse=',')))
  Log.info(paste0("Response: ", response_col))

  Log.info("Constructing the grid of gbm models...")
  cars_gbm_grid = h2o.grid("gbm", grid_id="gbm_grid_cars_test", x=predictors, y=response_col, training_frame=train,
                           validation_frame=valid, hyper_params=grid_space)
  testEnd()
}

doTest("PUBDEV-1844", test.pubdev_1844)
