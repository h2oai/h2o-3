context("metalearn")


test_that( "h2o.metalearn produces valid results (binomial)", {
  #testthat::skip_on_cran()
  
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
  #h2o.randomForest.1 <- function(..., ntrees = 100, seed = 1) h2oEnsemble::h2o.randomForest.wrapper(..., ntrees = ntrees, seed = seed)
  #h2o.gbm.1 <- function(..., ntrees = 100, seed = 1) h2oEnsemble::h2o.gbm.wrapper(..., ntrees = ntrees, seed = seed)
  #learner <- c("h2o.glm.wrapper", "h2o.randomForest.1", "h2o.gbm.1")  #this does not work w/ testthat bc functions are in wrong namespace
  learner <- c("h2o.glm.wrapper", "h2o.gbm.wrapper")  #this works bc functions exist in h2oEnsemble namespace
  metalearner_a <- "h2o.glm.wrapper"
  metalearner_b <- "h2o.gbm.wrapper"
  
  # Train an ensemble model with GLM metalearner
  fit_a <- h2o.ensemble(x = x, y = y,
                        training_frame = train,
                        learner = learner,
                        metalearner = metalearner_a)
  
  # Train an ensemble model with GBM metalearner
  fit_b <- h2o.ensemble(x = x, y = y,
                        training_frame = train,
                        learner = learner,
                        metalearner = metalearner_b)
  
  # Re-train the metalearner 
  refit_aa <- h2o.metalearn(fit_a, metalearner = metalearner_a)
  refit_ab <- h2o.metalearn(fit_a, metalearner = metalearner_b)
  refit_ba <- h2o.metalearn(fit_b, metalearner = metalearner_a)
  refit_bb <- h2o.metalearn(fit_b, metalearner = metalearner_b)
  
  # Test that model type is correct
  expect_true( inherits(fit_a$metafit, "H2OBinomialModel") )
  expect_true( inherits(fit_b$metafit, "H2OBinomialModel") )
  expect_true( inherits(refit_aa$metafit, "H2OBinomialModel") )
  expect_true( inherits(refit_ab$metafit, "H2OBinomialModel") )
  expect_true( inherits(refit_ba$metafit, "H2OBinomialModel") )
  expect_true( inherits(refit_bb$metafit, "H2OBinomialModel") )
  
  # Ensemble test AUC
  get_test_auc <- function(fit) {
    pred <- predict(fit, test)
    predictions <- as.data.frame(pred$pred)[,3]  #third column, p1 is P(Y==1)
    labels <- as.data.frame(test[,y])[,1]
    return(cvAUC::AUC(predictions = predictions , labels = labels))
  }
  
  # Check that if we re-order the steps, we get same results:
  
  # Test that refitting with identical metalearner produces identical results
  expect_equal( get_test_auc(fit_a), get_test_auc(refit_aa) )
  expect_equal( get_test_auc(fit_b), get_test_auc(refit_bb) )
  
  # Test that refitting with a new metalearner produces identical results
  expect_equal( get_test_auc(fit_a), get_test_auc(refit_ba) )
  expect_equal( get_test_auc(fit_b), get_test_auc(refit_ab) )
  
  # Grab the `levelone` data and compare manual metafit vs ensemble metafit:
  levelone <- fit_a$levelone
  metafit_a <- h2o.glm.wrapper(x = fit_a$learner, y = "y",
                               family = "binomial",
                               training_frame = levelone) 
  # Validate the GLM metalearner coef is the same
  expect_identical( h2o.coef(fit_a$metafit), h2o.coef(metafit_a) )
  
})



test_that( "h2o.metalearn produces valid results (gaussian)", {
  #testthat::skip_on_cran()
  
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
  #h2o.randomForest.1 <- function(..., ntrees = 100, seed = 1) h2oEnsemble::h2o.randomForest.wrapper(..., ntrees = ntrees, seed = seed)
  #h2o.gbm.1 <- function(..., ntrees = 100, seed = 1) h2oEnsemble::h2o.gbm.wrapper(..., ntrees = ntrees, seed = seed)
  #learner <- c("h2o.glm.wrapper", "h2o.randomForest.1", "h2o.gbm.1")  #this does not work w/ testthat bc functions are in wrong namespace
  learner <- c("h2o.glm.wrapper", "h2o.gbm.wrapper")  #this works bc functions exist in h2oEnsemble namespace
  metalearner_a <- "h2o.glm.wrapper"
  metalearner_b <- "h2o.gbm.wrapper"
  
  # Train an ensemble model with GLM metalearner
  fit_a <- h2o.ensemble(x = x, y = y,
                        training_frame = train,
                        learner = learner,
                        metalearner = metalearner_a)
  
  # Train an ensemble model with GBM metalearner
  fit_b <- h2o.ensemble(x = x, y = y,
                        training_frame = train,
                        learner = learner,
                        metalearner = metalearner_b)
  
  # Re-train the metalearner 
  refit_aa <- h2o.metalearn(fit_a, metalearner = metalearner_a)
  refit_ab <- h2o.metalearn(fit_a, metalearner = metalearner_b)
  refit_ba <- h2o.metalearn(fit_b, metalearner = metalearner_a)
  refit_bb <- h2o.metalearn(fit_b, metalearner = metalearner_b)
  
  # Test that model type is correct
  expect_true( inherits(fit_a$metafit, "H2ORegressionModel") )
  expect_true( inherits(fit_b$metafit, "H2ORegressionModel") )
  expect_true( inherits(refit_aa$metafit, "H2ORegressionModel") )
  expect_true( inherits(refit_ab$metafit, "H2ORegressionModel") )
  expect_true( inherits(refit_ba$metafit, "H2ORegressionModel") )
  expect_true( inherits(refit_bb$metafit, "H2ORegressionModel") )
  
  # Ensemble test AUC (even though this is a regression problem)
  get_test_auc <- function(fit) {
    pred <- predict(fit, test)
    predictions <- as.data.frame(pred$pred)[,1]
    labels <- as.data.frame(test[,y])[,1]
    return(cvAUC::AUC(predictions = predictions , labels = labels))
  }
  
  # Check that if we re-order the steps, we get same results:
  
  # Test that refitting with identical metalearner produces identical results
  expect_equal( get_test_auc(fit_a), get_test_auc(refit_aa) )
  expect_equal( get_test_auc(fit_b), get_test_auc(refit_bb) )
  
  # Test that refitting with a new metalearner produces identical results
  expect_equal( get_test_auc(fit_a), get_test_auc(refit_ba) )
  expect_equal( get_test_auc(fit_b), get_test_auc(refit_ab) )
  
  # Grab the `levelone` data and compare manual metafit vs ensemble metafit:
  levelone <- fit_a$levelone
  metafit_a <- h2o.glm.wrapper(x = fit_a$learner, y = "y",
                               family = "gaussian",
                               training_frame = levelone) 
  # Validate the GLM metalearner coef is the same
  expect_identical( h2o.coef(fit_a$metafit), h2o.coef(metafit_a) )
  
})

