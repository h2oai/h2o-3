context("ensemble-default")

test_that( "h2o.ensemble run with default args produces valid results (binomial)", {
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
  
  # Train an ensemble model with default args
  fit <- h2o.ensemble(x = x, y = y, training_frame = train)
  
  # Check that `fit` output is reasonable
  expect_equal( length(fit$learner), 4 )
  expect_equal( length(fit$basefits), 4 )
  expect_equal( dim(fit$levelone), c(5000, 5) )
  expect_true( inherits(fit$metafit, "H2OBinomialModel") )
  for (h2ofit in fit$basefits) {
    expect_true( inherits(h2ofit, "H2OBinomialModel") )
  }
  
  # Predict on test set
  pp <- predict(fit, test)
  expect_equal( dim(pp$pred), c(5000, 3) )
  expect_equal( dim(pp$basepred), c(5000, 4) )
  
  # Ensemble test AUC (not reproducible with default base learner library)
  predictions <- as.data.frame(pp$pred)[,3]  #third column, p1 is P(Y==1)
  labels <- as.data.frame(test[,y])[,1]
  auc <- cvAUC::AUC(predictions = predictions, labels = labels)
  expect_less_than(abs(auc - 0.78), 0.01)
  
  # Base learner test AUC (for comparison)
  base_auc <- sapply(seq(4), function(l) cvAUC::AUC(predictions = as.data.frame(pp$basepred)[,l], labels = labels)) 
  expect_less_than( max(base_auc), auc )
})


test_that( "h2o.ensemble run with default args produces valid results (gaussian)", {
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
  
  # Train an ensemble model with default args
  fit <- h2o.ensemble(x = x, y = y, training_frame = train)
  
  # Check that `fit` output is reasonable
  expect_equal( length(fit$learner), 4 )
  expect_equal( length(fit$basefits), 4 )
  expect_equal( dim(fit$levelone), c(5000, 5) )
  expect_true( inherits(fit$metafit, "H2ORegressionModel") )
  for (h2ofit in fit$basefits) {
    expect_true( inherits(h2ofit, "H2ORegressionModel") )
  }
  
  # Predict on test set
  pp <- predict(fit, test)
  expect_equal( dim(pp$pred), c(5000, 1) )
  expect_equal( dim(pp$basepred), c(5000, 4) )
  
  # Ensemble test AUC (not reproducible with default base learner library)
  # We still use AUC as a test here even though it's regression (testing only)
  predictions <- as.data.frame(pp$pred)[,1]  #first column for regression
  labels <- as.data.frame(test[,y])[,1]
  auc <- cvAUC::AUC(predictions = predictions, labels = labels)
  expect_less_than(abs(auc - 0.78), 0.01)
  
  # Base learner test AUC (for comparison)
  base_auc <- sapply(seq(4), function(l) cvAUC::AUC(predictions = as.data.frame(pp$basepred)[,l], labels = labels)) 
  expect_less_than( max(base_auc), auc )
})

