setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.checkpointing <- function() {
  cars <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  seed <- sample(1:1000000, 1)
  h2oTest.logInfo(paste0("runif seed: ",seed))
  s <- h2o.runif(cars, seed=seed)
  train <- cars[s > .2,]
  valid <- cars[s <= .2,]

  # choose the type model-building exercise (multinomial classification or regression). 0:regression, 1:binomial,
  # 2:multinomial
  problem <- sample(0:2,1)

  # pick the predictors and response column
  predictors <- c("displacement","power","weight","acceleration","year")
  if ( problem == 1 ) {
    response_col <- "economy_20mpg"
    distribution <- "bernoulli"
    train[,response_col] <- as.factor(train[response_col])
    valid[,response_col] <- as.factor(valid[response_col])
  } else if ( problem == 2 ) {
    response_col <- "cylinders"
    distribution <- "multinomial"
    train[,response_col] <- as.factor(train[response_col])
    valid[,response_col] <- as.factor(valid[response_col])
  } else {
    distribution <- "gaussian"
    response_col <- "economy"
  }

  print(paste0("Response column: ",response_col))

  # build first model
  ntrees1=sample(5:20,1)
  max_depth1=sample(2:5,1)
  min_rows1=sample(10:15,1)
  print(paste0("ntrees model 1: ",ntrees1))
  print(paste0("max_depth model 1: ",max_depth1))
  print(paste0("min_rows model 1: ",min_rows1))
  model1 <- h2o.gbm(x=predictors,y=response_col,training_frame=train,ntrees=ntrees1,max_depth=max_depth1,
                    min_rows=min_rows1,validation_frame=valid,distribution=distribution)

  # continue building the model
  ntrees2=ntrees1+sample(5:20,1)
  max_depth2=max_depth1
  min_rows2=min_rows1
  print(paste0("ntrees model 2: ",ntrees2))
  print(paste0("max_depth model 2: ",max_depth2))
  print(paste0("min_rows model 2: ",min_rows2))
  model2 <- h2o.gbm(x=predictors,y=response_col,training_frame=train,ntrees=ntrees2,max_depth=max_depth2,
                    min_rows=min_rows2,checkpoint=model1@model_id,validation_frame=valid, distribution=distribution)

  # build the equivalent model in one shot
  model3 <- h2o.gbm(x=predictors,y=response_col,training_frame=train,ntrees=ntrees2,max_depth=max_depth2,
                    min_rows=min_rows2,validation_frame=valid,distribution=distribution)

  a <- model2@model$validation_metrics
  b <- model3@model$validation_metrics
  print(a)
  print(b)
  if ( problem == 0 ) {       expect_mm_regression_equal(a, b)
  } else if ( problem == 1) { expect_mm_binomial_equal(a, b)
  } else {                    expect_mm_multinomial_equal(a, b) }

  
}

expect_mm_regression_equal <- function(a, b, msg) {
  expect_equal(a@metrics$model_category, b@metrics$model_category)
  expect_true(abs(a@metrics$MSE-b@metrics$MSE) < 1e-4*a@metrics$MSE)
  expect_true(abs(a@metrics$r2-b@metrics$r2) < 1e-4*a@metrics$r2)
}

expect_mm_binomial_equal <- function(a, b, msg) {
  cmA <- a@metrics$cm$table
  cmB <- b@metrics$cm$table
  expect_equal(cmA, cmB)
  expect_equal(a@metrics$model_category, b@metrics$model_category)
  expect_true(abs(h2o.mse(a)-h2o.mse(b)) < 1e-4*h2o.mse(a))
  expect_true(abs(h2o.r2(a)-h2o.r2(b)) < 1e-4)
  expect_true(abs(h2o.giniCoef(a)-h2o.giniCoef(b)) < 1e-4)
  expect_true(abs(h2o.logloss(a)-h2o.logloss(b)) < 1e-4*h2o.logloss(a))
  expect_true(abs(h2o.auc(a)-h2o.auc(b)) < 1e-4)
}

expect_mm_multinomial_equal <- function(a, b, msg) {
  cmA <- a@metrics$cm$table
  cmB <- b@metrics$cm$table
  expect_equal(cmA, cmB)
  expect_equal(a@metrics$model_category, b@metrics$model_category)
  expect_true(abs(h2o.mse(a)-h2o.mse(b)) < 1e-4*h2o.mse(a))
  expect_true(abs(h2o.r2(a)-h2o.r2(b)) < 1e-4)
  expect_true(abs(h2o.logloss(a)-h2o.logloss(b)) < 1e-4*h2o.logloss(a))
  expect_equal(a@metrics$hit_ratio_table$hit_ratio,b@metrics$hit_ratio_table$hit_ratio) ##not sure how to quickly add relative tolerance
}

h2oTest.doTest("Test GBM checkpointing", test.checkpointing)
