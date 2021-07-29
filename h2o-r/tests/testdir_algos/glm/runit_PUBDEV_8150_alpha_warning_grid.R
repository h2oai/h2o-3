setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This test is to make sure that we get a warning if we set alpha in hyper-parameter.
check.glm.grid.cars <- function() {
  cars <- h2o.uploadFile(locate("smalldata/junit/cars_20mpg.csv"))
  seed <- sample(1:1000000, 1)
  Log.info(paste0("runif seed: ",seed))
  r <- h2o.runif(cars,seed=seed)
  train <- cars[r > 0.2,]
  grid_space <- list()
  grid_space$alpha <- c(0.1, 0.5)
  predictors <- c("displacement","power","weight","acceleration","year")
  response_col <- "economy_20mpg"
  family <- "binomial"
  h2o_grid <- h2o.grid("glm", x=predictors, y=response_col, training_frame=train, family=family, hyper_params=grid_space)
  expect_warning(h2o.grid("glm", x=predictors, y=response_col, training_frame=train, family=family, hyper_params=grid_space))
}

doTest("GLM Grid Search setting alpha using cars dataset", check.glm.grid.cars)
