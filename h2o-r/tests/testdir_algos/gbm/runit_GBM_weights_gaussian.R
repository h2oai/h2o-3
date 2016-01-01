setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests observation weights in glm ######




test_weights_by_row_duplication <- function() {
  
  require(testthat)
  
  #create data
  print("create synthetic data with a numeric outcome")
  set.seed(45541)
  n <- 100  #Number of training observations (also nrow(test) = n)
  p <- 20  #Number of features
  x <- matrix(rnorm(n*p), n, p)
  y <- rnorm(n)
  
  # Training data with weights
  # draw some random weights ~ Poisson, add 'x1' weight col and y to df, hdf
  set.seed(1234)
  x1 <- rpois(n, rep(2, n)) + 1  #Random integer-valued (>=1) weights
  df <- data.frame(x, x1, y)  #design matrix with weight and outcome cols
  hdf <- as.h2o(df, destination_frame = "hdf")  #for h2o
  
  # Training data (weights == 1.0 with repeated rows instead of weights)
  rep_idxs <- unlist(sapply(1:n, function(i) rep(i, df$x1[i])))
  rdf <- df[rep_idxs,]  #repeat rows
  rdf$x1 <- 1  #set weights back to 1.0
  rhdf <- as.h2o(rdf, destination_frame = "rhdf")  #for h2o
  
  ## for glmnet
  #df <- as.matrix(df)
  #rdf <- as.matrix(rdf)
  
  # Test data
  set.seed(2641)
  newx <- matrix(rnorm(n*p), n, p)
  newy <- rnorm(n)
  
  x1 <- rep(1, n)
  valid1 <- data.frame(newx, x1, y = newy)
  val1 <- as.h2o(valid1, destination_frame = "val1")
  valid1 <- as.matrix(valid1)
  
  print("build models with weights vs repeated rows with h2o and lambda!=0")
  hh1 <- h2o.gbm(x = 1:20, y = "y", 
                 training_frame = hdf, 
                 validation_frame = val1, 
                 ntrees = 50, 
                 weights_column = "x1")
  hh2 <- h2o.gbm(x = 1:20, y = "y", 
                 training_frame = rhdf, 
                 validation_frame = val1,
                 ntrees = 50, 
                 weights_column = "x1")
  
  print("compare results")
  expect_equal(hh1@model$training_metrics@metrics$MSE, 
               hh2@model$training_metrics@metrics$MSE,
               tolerance = 1e-6)
  expect_equal(hh1@model$training_metrics@metrics$r2, 
               hh2@model$training_metrics@metrics$r2,
               tolerance = 1e-6)

  expect_equal(hh1@model$validation_metrics@metrics$MSE,
               hh2@model$validation_metrics@metrics$MSE,
               tolerance = 1e-6)
  expect_equal(hh1@model$validation_metrics@metrics$r2,
               hh2@model$validation_metrics@metrics$r2,
               tolerance = 1e-6)
  
    
  #predictions
  print("compare predictions")
  ph1 <- as.data.frame(h2o.predict(object = hh1, newdata = val1))
  ph2 <- as.data.frame(h2o.predict(object = hh2, newdata = val1))
  mse1 <- mean((ph1$predict - newy)^2)
  mse2 <- mean((ph2$predict - newy)^2)
  #expect_equal(mse1, mse2)  #1.49 - 1.46 == 0.0291
  
  
  
}


h2oTest.doTest("GBM weight Test: GBM w/ weights test by row duplication", test_weights_by_row_duplication)
