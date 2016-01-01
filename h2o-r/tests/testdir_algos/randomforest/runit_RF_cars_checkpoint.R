setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



test.checkpointing <- function() {
  cars <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars_20mpg.csv"))
  s <- h2o.runif(cars)
  train <- cars[s > .2,]
  valid <- cars[s <= .2,]

  # choose the type model-building exercise (multinomial classification or regression). 0:regression, 1:binomial,
  # 2:multinomial
  problem <- sample(0:2,1)

  # pick the predictors and response column
  predictors <- c("displacement","power","weight","acceleration","year")
  if ( problem == 1 ) {
    response_col <- "economy_20mpg"
    train[,response_col] <- as.factor(train[response_col])
    valid[,response_col] <- as.factor(valid[response_col])
  } else if ( problem == 2 ) {
    response_col <- "cylinders"
    train[,response_col] <- as.factor(train[response_col])
    valid[,response_col] <- as.factor(valid[response_col])
  } else {
    response_col = "economy"
  }

  print(paste0("Response column: ",response_col))

  # build first model
  ntrees1=sample(5:20,1)
  max_depth1=sample(2:5,1)
  min_rows1=sample(10:15,1)
  print(paste0("ntrees model 1: ",ntrees1))
  print(paste0("max_depth model 1: ",max_depth1))
  print(paste0("min_rows model 1: ",min_rows1))
  model1 <- h2o.randomForest(x=predictors,y=response_col,training_frame=train,ntrees=ntrees1,max_depth=max_depth1,
                             min_rows=min_rows1,validation_frame=valid,seed=123)

  # continue building the model
  ntrees2=ntrees1+sample(5:20,1)
  max_depth2=max_depth1
  min_rows2=min_rows1
  print(paste0("ntrees model 2: ",ntrees2))
  print(paste0("max_depth model 2: ",max_depth2))
  print(paste0("min_rows model 2: ",min_rows2))
  model2 <- h2o.randomForest(x=predictors,y=response_col,training_frame=train,ntrees=ntrees2,max_depth=max_depth2,
                             min_rows=min_rows2,checkpoint=model1@model_id,validation_frame=valid,seed=123)

  # build the equivalent model in one shot
  model3 <- h2o.randomForest(x=predictors,y=response_col,training_frame=train,ntrees=ntrees2,max_depth=max_depth2,
                             min_rows=min_rows2,validation_frame=valid,seed=123)

  a <- model2@model$validation_metrics
  b <- model3@model$validation_metrics
  if ( problem == 0 ) {       expect_mm_regression_equal(a, b)
  } else if ( problem == 1) { expect_mm_binomial_equal(a, b)
  } else {                    expect_mm_multinomial_equal(a, b) }

  
}

expect_mm_regression_equal <- function(a, b, msg) {
  expect_equal(a@metrics$model_category, b@metrics$model_category)
  expect_equal(a@metrics$MSE, b@metrics$MSE)
  expect_equal(a@metrics$r2, b@metrics$r2)
}

expect_mm_binomial_equal <- function(a, b, msg) {
  cmA <- a@metrics$cm$table
  cmB <- b@metrics$cm$table
  expect_equal(cmA, cmB)
  expect_equal(a@metrics$model_category, b@metrics$model_category)
  expect_equal(a@metrics$MSE, b@metrics$MSE)
  expect_equal(a@metrics$r2, b@metrics$r2)
  expect_equal(a@metrics$giniCoef, b@metrics$giniCoef)
  expect_equal(a@metrics$logloss, b@metrics$logloss)
  expect_equal(a@metrics$auc, b@metrics$auc)
}

expect_mm_multinomial_equal <- function(a, b, msg) {
  cmA <- a@metrics$cm$table
  cmB <- b@metrics$cm$table
  expect_equal(cmA, cmB)
  expect_equal(a@metrics$model_category, b@metrics$model_category)
  expect_equal(a@metrics$MSE, b@metrics$MSE)
  expect_equal(a@metrics$r2, b@metrics$r2)
  expect_equal(a@metrics$hit_ratio_table$hit_ratio, b@metrics$hit_ratio_table$hit_ratio)
  expect_equal(a@metrics$logloss, b@metrics$logloss)
}

h2oTest.doTest("Test DRF checkpointing", test.checkpointing)
