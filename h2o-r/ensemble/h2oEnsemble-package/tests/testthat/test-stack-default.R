context("stack-default")

test_that( "h2o.stack run with default args produces valid results (binomial)", {
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
  
  # Specify the base learner library & the metalearner
  # Let's use a reproducible library (set seed on RF and GBM):
  metalearner <- "h2o.glm.wrapper"
  h2o.randomForest.1 <- function(..., ntrees = 20, seed = 1) h2oEnsemble::h2o.randomForest.wrapper(..., ntrees = ntrees, seed = seed)
  h2o.gbm.1 <- function(..., ntrees = 20, seed = 1) h2oEnsemble::h2o.gbm.wrapper(..., ntrees = ntrees, seed = seed)
  learner <- c("h2o.glm.wrapper", "h2o.randomForest.1", "h2o.gbm.1")  #this does not work w/ testthat bc functions are in wrong namespace
  nfolds <- 5
  
  # Train an ensemble model with default args:  
  fit <- h2o.ensemble(x = x, y = y, training_frame = train,
                      family = family,
                      learner = learner,
                      metalearner = metalearner,
                      cvControl = list(V = nfolds))

  
  # Now create the GLM, RF, GBM ensemble/stack manually:
  
  # TO DO: Check why h2o.glm doesn't match exactly to h2o.glm.wrapper
  # Some other default parameter is not being set; until resolved, 
  # use h2o.glm.wrapper to check exactness between h2o.ensemble and h2o.stack
  #glm1 <- h2o.glm(x = x, y = y, family = "binomial", 
  #                training_frame = train,
  #                max_iterations = 50,
  #                nfolds = nfolds,
  #                fold_assignment = "Modulo",
  #                keep_cross_validation_predictions = TRUE)
  glm2 <- h2o.glm.wrapper(x = x, y = y, family = "binomial", 
                  training_frame = train,
                  max_iterations = 50,
                  nfolds = nfolds,
                  fold_assignment = "Modulo",
                  keep_cross_validation_predictions = TRUE)
  
  rf1 <- h2o.randomForest(x = x, y = y,
                          training_frame = train,
                          seed = 1,
                          ntrees = 20,
                          nfolds = nfolds,
                          fold_assignment = "Modulo",
                          keep_cross_validation_predictions = TRUE)
  
  gbm1 <- h2o.gbm(x = x, y = y, distribution = "bernoulli",
                  training_frame = train,
                  seed = 1,
                  ntrees = 20,
                  nfolds = nfolds,
                  fold_assignment = "Modulo",
                  keep_cross_validation_predictions = TRUE)
  
  #models <- c(glm1, rf1, gbm1)
  models <- c(glm2, rf1, gbm1)
  stack <- h2o.stack(models = models, 
                     metalearner = metalearner, 
                     response_frame = train[,y])
  
  perf_fit <- h2o.ensemble_performance(fit, newdata = test)
  perf_stack <- h2o.ensemble_performance(stack, newdata = test)
    
  # Check that base fit performance is identical
  expect_equal( perf_fit$base[[1]]@metrics$AUC, perf_stack$base[[1]]@metrics$AUC )
  expect_equal( perf_fit$base[[2]]@metrics$AUC, perf_stack$base[[2]]@metrics$AUC )
  expect_equal( perf_fit$base[[3]]@metrics$AUC, perf_stack$base[[3]]@metrics$AUC )

  # Check that ensemble performance is near-identical
  expect_equal(perf_fit$ensemble@metrics$AUC, 
               perf_stack$ensemble@metrics$AUC, 
               tolerance = 0.0005)
})


test_that( "h2o.ensemble run with default args produces valid results (gaussian)", {
  testthat::skip_on_cran()

  # Import a sample binary outcome train/test set into H2O
  h2o.init(nthreads = -1)
  train_csv <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_train_5k.csv"
  test_csv <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_test_5k.csv"
  train <- h2o.importFile(train_csv)
  test <- h2o.importFile(test_csv)
  y <- "response"
  x <- setdiff(names(train), y)
  family <- "gaussian"

  # Specify the base learner library & the metalearner
  # Let's use a reproducible library (set seed on RF and GBM):
  metalearner <- "h2o.glm.wrapper"
  h2o.randomForest.1 <- function(..., ntrees = 20, seed = 1) h2oEnsemble::h2o.randomForest.wrapper(..., ntrees = ntrees, seed = seed)
  h2o.gbm.1 <- function(..., ntrees = 20, seed = 1) h2oEnsemble::h2o.gbm.wrapper(..., ntrees = ntrees, seed = seed)
  learner <- c("h2o.glm.wrapper", "h2o.randomForest.1", "h2o.gbm.1")  #this does not work w/ testthat bc functions are in wrong namespace
  nfolds <- 5
  
  # Train an ensemble model with default args:  
  fit <- h2o.ensemble(x = x, y = y, training_frame = train,
                      family = family,
                      learner = learner,
                      metalearner = metalearner,
                      cvControl = list(V = nfolds))
  
  
  # Now create the GLM, RF, GBM ensemble/stack manually:
  
  # TO DO: Check why h2o.glm doesn't match exactly to h2o.glm.wrapper
  # Some other default parameter is not being set; until resolved, 
  # use h2o.glm.wrapper to check exactness between h2o.ensemble and h2o.stack
  #glm1 <- h2o.glm(x = x, y = y, family = "binomial", 
  #                training_frame = train,
  #                max_iterations = 50,
  #                nfolds = nfolds,
  #                fold_assignment = "Modulo",
  #                keep_cross_validation_predictions = TRUE)
  glm2 <- h2o.glm.wrapper(x = x, y = y, family = "gaussian", 
                          training_frame = train,
                          max_iterations = 50,
                          nfolds = nfolds,
                          fold_assignment = "Modulo",
                          keep_cross_validation_predictions = TRUE)
  
  rf1 <- h2o.randomForest(x = x, y = y,
                          training_frame = train,
                          seed = 1,
                          ntrees = 20,
                          nfolds = nfolds,
                          fold_assignment = "Modulo",
                          keep_cross_validation_predictions = TRUE)
  
  gbm1 <- h2o.gbm(x = x, y = y, distribution = "gaussian",
                  training_frame = train,
                  seed = 1,
                  ntrees = 20,
                  nfolds = nfolds,
                  fold_assignment = "Modulo",
                  keep_cross_validation_predictions = TRUE)
  
  #models <- c(glm1, rf1, gbm1)
  models <- c(glm2, rf1, gbm1)
  stack <- h2o.stack(models = models, 
                     metalearner = metalearner, 
                     response_frame = train[,y])
  
  perf_fit <- h2o.ensemble_performance(fit, newdata = test)
  perf_stack <- h2o.ensemble_performance(stack, newdata = test)
  
  # Check that base fit performance is identical
  expect_equal( perf_fit$base[[1]]@metrics$MSE, perf_stack$base[[1]]@metrics$MSE )
  expect_equal( perf_fit$base[[2]]@metrics$MSE, perf_stack$base[[2]]@metrics$MSE )
  expect_equal( perf_fit$base[[3]]@metrics$MSE, perf_stack$base[[3]]@metrics$MSE )
  
  # Check that ensemble performance is near-identical
  expect_equal(perf_fit$ensemble@metrics$MSE, 
               perf_stack$ensemble@metrics$MSE, 
               tolerance = 0.00005)
})

