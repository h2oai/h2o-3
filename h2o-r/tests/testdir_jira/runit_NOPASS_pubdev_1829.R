setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



test.pubdev.1829 <- function(conn){

  cars <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  predictors <- c("displacement","power","weight","acceleration","year")
  response_col <- "economy_20mpg"
  distribution <- "bernoulli"
  seed <- 383467
  s <- h2o.runif(cars, seed=seed)
  train <- cars[s > .2,]
  valid <- cars[s <= .2,]
  train[,response_col] <- as.factor(train[response_col])
  valid[,response_col] <- as.factor(valid[response_col])


  # build first model
  ntrees1 <- 11
  max_depth1 <- 5
  min_rows1 <- 14
  model1 <- h2o.gbm(x=predictors,y=response_col,training_frame=train,ntrees=ntrees1,max_depth=max_depth1,
                    min_rows=min_rows1,validation_frame=valid,distribution=distribution)

  # continue building the model
  ntrees2 <- 27
  max_depth2 <- 5
  min_rows2 <-14
  model2 <- h2o.gbm(x=predictors,y=response_col,training_frame=train,ntrees=ntrees2,max_depth=max_depth2,
                    min_rows=min_rows2,checkpoint=model1@model_id,validation_frame=valid, distribution=distribution)


  # build the equivalent model in one shot
  model3 <- h2o.gbm(x=predictors,y=response_col,training_frame=train,ntrees=ntrees2,max_depth=max_depth2,
                    min_rows=min_rows2,validation_frame=valid,distribution=distribution)

  print("Model2:")
  print(model2)

  print("Model3:")
  print(model3)

  a <- model2@model$validation_metrics
  b <- model3@model$validation_metrics

  expect_equal(a@metrics$MSE, b@metrics$MSE)
  expect_equal(a@metrics$r2, b@metrics$r2)
  expect_equal(a@metrics$logloss, b@metrics$logloss)

  
}

h2oTest.doTest("PUBDEV-1829", test.pubdev.1829)
