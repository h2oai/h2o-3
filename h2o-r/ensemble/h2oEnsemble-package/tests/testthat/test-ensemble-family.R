context("ensemble-family")

test_that( "h2o.ensemble family argument works for binary classification", {
  testthat::skip_on_cran()
  
  # Import a sample binary outcome train/test set into H2O
  h2o.init(nthreads = -1)
  train_csv <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_train_5k.csv"
  train <- h2o.importFile(train_csv)
  y <- "response"
  x <- setdiff(names(train), y)
  train[,y] <- as.factor(train[,y])

  # Should work
  for (family in c("AUTO", "binomial")) {
    fit <- h2o.ensemble(x = x, y = y, training_frame = train, family = family)
  }
  # All these should fail
  for (family in c("gaussian", "quasibinomial", "poisson", "gamma", "tweedie", "laplace", "quantile", "huber")) {
    expect_error(h2o.ensemble(x = x, y = y, training_frame = train, family = family))
  }
  
  # A few more cases in which it should fail
  expect_error(h2o.ensemble(x = x, y = y, training_frame = train, family = "foo"))
  expect_error(h2o.ensemble(x = x, y = y, training_frame = train, family = c("a", "b")))
})


test_that( "h2o.ensemble family argument works for regression", {
  testthat::skip_on_cran()
  
  # Import a sample train/test set into H2O
  h2o.init(nthreads = -1)
  train_csv <- "https://h2o-public-test-data.s3.amazonaws.com/smalldata/testng/higgs_train_5k.csv"
  train <- h2o.importFile(train_csv)
  y <- "response"
  x <- setdiff(names(train), y)
  
  # Should work
  for (family in c("AUTO", "gaussian", "quasibinomial", "poisson", "tweedie", "laplace", "quantile", "huber")) {
    fit <- h2o.ensemble(x = x, y = y, training_frame = train, family = family)
  }
  # gamma requires min(train[,y]) > 0, but here we have 0's & 1's, so this should fail
  expect_error(h2o.ensemble(x = x, y = y, training_frame = train, family = "gamma"))
  train[,y] <- train[,y] + 1  # update the response so we can test gamma
  fit <- h2o.ensemble(x = x, y = y, training_frame = train, family = "gamma")
  
  # A few more cases in which it should fail
  expect_error(h2o.ensemble(x = x, y = y, training_frame = train, family = "binomial"))
  expect_error(h2o.ensemble(x = x, y = y, training_frame = train, family = "foo"))
  expect_error(h2o.ensemble(x = x, y = y, training_frame = train, family = c("a", "b")))
})

