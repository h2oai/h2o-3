setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev.1846.deeplearning <- function(conn){

  cars <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
  grid_space <- list()
  grid_space$activation <- c("Rectifier", "Tanh", "Foo")
  grid_space$distribution <- 'bernoulli'
  predictors <- c("displacement","power","weight","acceleration","year")
  response_col <- "economy_20mpg"
  h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col, training_frame=cars,
           hyper_params=grid_space)

  testEnd()
}

doTest("PUBDEV-1846", test.pubdev.1846.deeplearning)