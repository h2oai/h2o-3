context("ensemble-wrappers")

test_that( "h2o.ensemble wrappers support passing in a subset of the predictor columns", {
  testthat::skip_on_cran()
  
  # Import a sample binary outcome train/test set into H2O
  h2o.init(nthreads = -1)
  train_csv <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_train_5k.csv"
  test_csv <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_test_5k.csv"
  train <- h2o.importFile(train_csv)
  test <- h2o.importFile(test_csv)
  y <- "response"
  x <- setdiff(names(train), y)
  family <- "binomial"
  train[,y] <- as.factor(train[,y])
  test[,y] <- as.factor(test[,y])
  
  # Different sets of predictors
  x0 <- x
  x1 <- setdiff(x, c("x1", "x2", "x3", "x4", "x5"))
  x2 <- setdiff(x, c("x6", "x7", "x8", "x9", "x10"))
  x3 <- setdiff(x, c("x1", "x2", "x3", "x4", "x5", "x6", "x7", "x8", "x9", "x10"))
  
  # Different custom learner wrappers
  gbm0 <- function(..., ntrees = 100, seed = 1, x = x0) h2o.gbm.wrapper(..., ntrees = ntrees, seed = seed, x = x)
  gbm1 <- function(..., ntrees = 100, seed = 1, x = x1) h2o.gbm.wrapper(..., ntrees = ntrees, seed = seed, x = x)
  gbm2 <- function(..., ntrees = 100, seed = 1, x = x2) h2o.gbm.wrapper(..., ntrees = ntrees, seed = seed, x = x)
  gbm3 <- function(..., ntrees = 100, seed = 1, x = x3) h2o.gbm.wrapper(..., ntrees = ntrees, seed = seed, x = x)
  gbm4 <- function(..., ntrees = 100, seed = 1, x = c("x1", "x2", "x3")) h2o.gbm.wrapper(..., ntrees = ntrees, seed = seed, x = x)
  
  # Specify the base learner library & the metalearner
  learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper", 
               "gbm0", "gbm1", "gbm2", "gbm3", "gbm4")
  metalearner <- "h2o.glm.wrapper"
  family <- "binomial"
  
  
  # Train an ensemble model with default args
  fit <- h2o.ensemble(x = x, y = y, training_frame = train,
                      family = family, learner = learner,
                      metalearner = metalearner)
  
  # Check that subsets are used correctly in base fits
  expect_equal( length(fit$basefits[[1]]@allparameters$x), 29 )
  expect_equal( length(fit$basefits[[2]]@allparameters$x), 29 )
  expect_equal( length(fit$basefits[[3]]@allparameters$x), 29 )
  expect_equal( length(fit$basefits[[4]]@allparameters$x), 24 )
  expect_equal( length(fit$basefits[[5]]@allparameters$x), 24 )
  expect_equal( length(fit$basefits[[6]]@allparameters$x), 19 )
  expect_equal( length(fit$basefits[[7]]@allparameters$x), 4 )

})