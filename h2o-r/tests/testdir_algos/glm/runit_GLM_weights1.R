setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests observation weights in glm ######


# TODO: Deduplicate code for lambda=0 and lambda>=0 cases

test_weights_vs_glmnet <- function() {
  
  require(testthat)
  require(glmnet)
  
  #create training data
  print("create synthetic data")
  set.seed(45541)
  n <- 100  #Number of training observations (also nrow(test) = n)
  p <- 20  #Number of features
  x <- matrix(rnorm(n*p), n, p)
  y <- rnorm(n)
  
  x1 <- rep(1, n)  #weight vector (all weights = 1.0)
  df <- data.frame(x, x1, y)  #design matrix with weight and outcome cols
  hdf <- as.h2o(df, destination_frame = "hdf")  #for h2o
  df <- as.matrix(df)  #for glmnet
  
  # create test data
  set.seed(2641)
  newx <- matrix(rnorm(n*p), n, p)
  newy <- rnorm(n)
  
  x1 <- rep(1,100)
  valid1 <- data.frame(newx, x1, y = newy)
  val1 <- as.h2o(valid1, destination_frame = "val1")
  valid1 <- as.matrix(valid1)
  
  x1 <- rep(100,100)
  valid2 <- data.frame(newx, x1, y = newy)
  val2 <- as.h2o(valid2, destination_frame = "val2")
  valid2 <- as.matrix(valid2)
  
  x1 <- seq(1:100)
  valid3 <- data.frame(newx, x1, y = newy)
  val3 <- as.h2o(valid3, destination_frame = "val3")
  valid3 <- as.matrix(valid3)
  
  #lambda=0
  print("build models with weights in h2o and R with lambda=0")
  gg <- glmnet(x = df[,1:20], y = df[,22], alpha = 0.5, 
               lambda = 0, weights = df[,21])
  hh1 <- h2o.glm(x = 1:20, y = "y", 
                 training_frame = hdf, 
                 validation_frame = val1, 
                 alpha = 0.5, 
                 lambda = 0, 
                 weights_column = "x1")
  hh2 <- h2o.glm(x = 1:20, y = "y", 
                 training_frame = hdf, 
                 validation_frame = val2,
                 alpha = 0.5, 
                 lambda = 0,
                 weights_column = "x1")
  
  print("compare results")
  expect_equal(gg$nulldev, 
               hh1@model$training_metrics@metrics$null_deviance)
  expect_equal(deviance(gg),
               hh1@model$training_metrics@metrics$residual_deviance,
               tolerance = 0.001)
  expect_equal(as.vector(hh1@model$coefficients[-1]),
               as.vector(gg$beta),
               tolerance = 0.01)
  expect_equal((100*hh1@model$validation_metrics@metrics$residual_deviance),
               hh2@model$validation_metrics@metrics$residual_deviance)
  
  #predictions
  print("compare predictions")
  ph1 <- as.data.frame(h2o.predict(object = hh1, newdata = val1))
  ph2 <- as.data.frame(h2o.predict(object = hh1, newdata = val2))
  ph3 <- as.data.frame(h2o.predict(object = hh1, newdata = val3))
  expect_equal(ph1, ph2)
  expect_equal(ph2, ph3)
  expect_equal(ph3, ph1)
  pr <- predict(object = gg, newx = valid3[,1:20], type = "response")
  expect_equal(min(pr), min(ph3), tolerance = 0.001)
  expect_equal(max(pr), max(ph3), tolerance = 0.001)
  expect_equal(mean(pr), mean(ph3$predict), tolerance = 0.001)
  
  
  # lambda!=0
  lambda <- 0.02984
  print("build models with weights in h2o and R with lambda!=0")
  gg <- glmnet(x = df[,1:20], y = df[,22], alpha = 0.5, 
               lambda = lambda, weights = df[,21])
  hh1 <- h2o.glm(x = 1:20, y = "y",
                 training_frame = hdf, 
                 validation_frame = val1,
                 alpha = 0.5, 
                 lambda = lambda,
                 weights_column = "x1")
  hh2 <- h2o.glm(x = 1:20,
                 y = "y",
                 training_frame = hdf, 
                 validation_frame = val2,
                 alpha = 0.5, 
                 lambda = lambda,
                 weights_column = "x1")
  
  print("compare results w/ lambda!=0")
  expect_equal(deviance(gg), 
               hh1@model$training_metrics@metrics$residual_deviance, 
               tolerance = 0.001)
  expect_equal(hh1@model$training_metrics@metrics$null_deviance, 
               hh2@model$training_metrics@metrics$null_deviance)
  expect_equal(hh1@model$training_metrics@metrics$residual_deviance, 
               hh2@model$training_metrics@metrics$residual_deviance)
  expect_equal((100*hh1@model$validation_metrics@metrics$residual_deviance), 
               hh2@model$validation_metrics@metrics$residual_deviance)
  expect_equal(hh1@model$validation_metrics@metrics$MSE, 
               hh2@model$validation_metrics@metrics$MSE)
  # TO DO: Add the rest of the expect_equals from test function below this
  
  #predictions
  print("compare predictions")
  ph1 <- as.data.frame(h2o.predict(object = hh1, newdata = val1))
  ph3 <- as.data.frame(h2o.predict(object = hh1, newdata = val3))
  expect_equal(ph3, ph1)
  pr <- predict(object = gg,
                newx = valid3[,1:20],
                type = "response")
  expect_equal(mean(pr), mean(ph3$predict), tolerance = 0.01)
  
  
}

h2oTest.doTest("GLM weight Test: GLM w/ weights vs glmnet", test_weights_vs_glmnet)
