setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

test.pubdev.1846.deeplearning <- function(conn){

  cars <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
  grid_space <- list()
  grid_space$activation <- c("Rectifier", "Tanh", "Foo")
  grid_space$distribution <- 'bernoulli'

  grid_space_size <- length(grid_space$activation) * length(grid_space$distribution)

  predictors <- c("displacement","power","weight","acceleration","year")
  response_col <- "economy_20mpg"
  expect_error(h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col, training_frame=cars,
                          hyper_params=grid_space, do_hyper_params_check = T))
  gg <- h2o.grid("deeplearning", grid_id="deeplearning_grid_cars_test", x=predictors, y=response_col, training_frame=cars,
           hyper_params=grid_space, do_hyper_params_check = F)
  # All params should fail right now
  gg_size <- length(gg@failed_params)
  expect_equal(grid_space_size, gg_size)

  testEnd()
}

doTest("PUBDEV-1846", test.pubdev.1846.deeplearning)
