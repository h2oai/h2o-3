setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.2075 <- function(conn){

  cars <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  seed <- 691895
  r <- h2o.runif(cars,seed=seed)
  train <- cars[r > 0.2,]
  predictors <- c("displacement","power","weight","acceleration","year")
  response_col <- "economy_20mpg"
  grid_space <- list()
  grid_space$ntrees <- c(5, 2, 3)
  grid_space$max_depth <- c(4, 1, 5)
  grid_space$nbins <- c(6, 4, 3)
  grid_space$nbins_cats <- c(370, 449)
  grid_space$mtries <- c(2, 4, 3)
  grid_space$sample_rate <- c(0.327667, 0.735594, 0.415836)
  train[,response_col] <- as.factor(train[,response_col])
  cars_drf_grid <- h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col,
                            training_frame=train, hyper_params=grid_space)

  h2oTest.logInfo("Expect 486 models to be constructed")
  num_models <- length(cars_drf_grid@model_ids)
  h2oTest.logInfo(paste0(num_models," models were constructed"))
  expect_equal(486, num_models)

}

h2oTest.doTest("PUBDEV-2075", test.pubdev.2075)
