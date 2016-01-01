setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.2075 <- function(conn){

  cars <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  seed <- 415483
  r <- h2o.runif(cars,seed=seed)
  train <- cars[r > 0.2,]
  valid <- cars[r <= 0.2,]
  predictors <- c("displacement","power","weight","acceleration","year")
  response_col <- "economy_20mpg"
  grid_space <- list()
  grid_space$nbins <- c(8, 7)
  grid_space$nbins_cats <- c(551, 478)
  train[,response_col] <- as.factor(train[,response_col])
  valid[,response_col] <- as.factor(valid[,response_col])
  cars_drf_grid <- h2o.grid("randomForest", grid_id="drf_grid_cars_test", x=predictors, y=response_col,
                            ntrees=3, max_depth=2, sample_rate=0.346999,
                           training_frame=train, validation_frame=valid, hyper_params=grid_space)

  h2oTest.logInfo("Expect 4 models to be constructed")
  num_models <- length(cars_drf_grid@model_ids)
  h2oTest.logInfo(paste0(num_models," models were constructed"))
  expect_equal(4, num_models)

  
}

h2oTest.doTest("PUBDEV-2075", test.pubdev.2075)
