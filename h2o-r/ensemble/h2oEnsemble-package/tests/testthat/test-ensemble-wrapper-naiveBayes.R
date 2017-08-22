context("ensemble-wrappers-naiveBayes")


test_that( "The Naive Bayes wrapper is working", {
  testthat::skip_on_cran()
  
  # Import a sample binary outcome train/test set into H2O
  h2o.init(nthreads = -1)
  #train <- h2o.importFile("/Users/me/data/higgs/higgs_train_5k.csv", destination_frame = "train")
  #test <- h2o.importFile("/Users/me/data/higgs/higgs_test_5k.csv", destination_frame = "test")
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
  learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper", 
               "h2o.naiveBayes.wrapper")
  metalearner <- "h2o.glm.wrapper"
  
  
  # Train an ensemble model with default args
  fit <- h2o.ensemble(x = x, y = y, training_frame = train,
                      family = family, learner = learner,
                      metalearner = metalearner)
  
  # Evaluate performance on a test set
  perf <- h2o.ensemble_performance(fit, newdata = test) 
  
  # Check expected test AUC on the Naive Bayes learner
  test_nb_auc <- h2o.auc(perf$base[[3]])
  expect_equal(test_nb_auc, 0.6524723, tolerance = 0.0005)
  
  
  # Specify wrappers in a different order
  learner <- c("h2o.naiveBayes.wrapper", "h2o.randomForest.wrapper", 
               "h2o.glm.wrapper")
  
  # Train an ensemble model with default args
  fit <- h2o.ensemble(x = x, y = y, training_frame = train,
                      family = family, learner = learner,
                      metalearner = metalearner)
  
  # Evaluate performance on a test set
  perf <- h2o.ensemble_performance(fit, newdata = test) 
  
  # Check expected test AUC on the Naive Bayes learner
  test_nb_auc <- h2o.auc(perf$base[[1]])
  expect_equal(test_nb_auc, 0.6524723, tolerance = 0.0005)
  
  # Check a custom naiveBayes wrappers
  h2o.naiveBayes.1 <- function(..., laplace = 0.001) h2o.naiveBayes.wrapper(..., laplace = laplace)

  learner <- c("h2o.naiveBayes.1", "h2o.randomForest.wrapper", 
               "h2o.glm.wrapper")
  
  # Train an ensemble model with default args
  fit <- h2o.ensemble(x = x, y = y, training_frame = train,
                      family = family, learner = learner,
                      metalearner = metalearner)
  
  # Evaluate performance on a test set
  perf <- h2o.ensemble_performance(fit, newdata = test) 
  
  # Check expected test AUC on the Naive Bayes learner
  test_nb_auc <- h2o.auc(perf$base[[1]])
  expect_equal(test_nb_auc, 0.6524723, tolerance = 0.0005)
  
})



test_that( "A Naive Bayes wrapper in a regression problem gets handled properly", {
  testthat::skip_on_cran()
  
  # Import a sample binary outcome train/test set into H2O
  h2o.init(nthreads = -1)
  #train <- h2o.importFile("/Users/me/data/higgs/higgs_train_5k.csv", destination_frame = "train")
  #test <- h2o.importFile("/Users/me/data/higgs/higgs_test_5k.csv", destination_frame = "test")
  train_csv <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_train_5k.csv"
  test_csv <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_test_5k.csv"
  train <- h2o.importFile(train_csv)
  test <- h2o.importFile(test_csv)
  y <- "response"
  x <- setdiff(names(train), y)
  family <- "gaussian"
  # Leave the response encoded as numeric so regression will be performed
  

  # Train a regression problem using h2o.naiveBayes.wrapper
  
  # Specify the base learner library & the metalearner
  learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper", 
               "h2o.naiveBayes.wrapper")
  metalearner <- "h2o.glm.wrapper"
  
  
  # Train an ensemble model with default args
  expect_error(fit <- h2o.ensemble(x = x, y = y, training_frame = train,
                      family = family, learner = learner,
                      metalearner = metalearner))

  
  # Use the wrapper as a metalearner instead:
  learner <- c("h2o.glm.wrapper", "h2o.randomForest.wrapper", 
               "h2o.gbm.wrapper")
  metalearner <- "h2o.naiveBayes.wrapper"
  
  
  # Train an ensemble model with default args
  expect_error(fit <- h2o.ensemble(x = x, y = y, training_frame = train,
                      family = family, learner = learner,
                      metalearner = metalearner))
  
  
})