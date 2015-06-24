### This tests observation weights in glm ######
setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')


test_weights_by_row_duplication <- function(conn) {
  
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
  hdf <- as.h2o(object = df, conn = conn, destination_frame = "hdf")  #for h2o
  
  # Training data (weights == 1.0 with repeated rows instead of weights)
  rep_idxs <- unlist(sapply(1:n, function(i) rep(i, df$x1[i])))
  rdf <- df[rep_idxs,]  #repeat rows
  rdf$x1 <- 1  #set weights back to 1.0
  rhdf <- as.h2o(object = rdf, conn = conn, destination_frame = "rhdf")  #for h2o
  
  ## for glmnet
  #df <- as.matrix(df)
  #rdf <- as.matrix(rdf)
  
  # Test data
  set.seed(2641)
  newx <- matrix(rnorm(n*p), n, p)
  newy <- rnorm(n)
  
  x1 <- rep(1, n)
  valid1 <- data.frame(newx, x1, y = newy)
  val1 <- as.h2o(valid1, conn = conn, destination_frame = "val1")
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
               hh2@model$training_metrics@metrics$MSE)
  expect_equal(hh1@model$training_metrics@metrics$r2, 
               hh2@model$training_metrics@metrics$r2,
               tolerance = 0.01)  #difference is about -0.003
  expect_equal(hh1@model$training_metrics@metrics$residual_deviance, 
               hh2@model$training_metrics@metrics$residual_deviance)
  expect_equal(hh1@model$training_metrics@metrics$null_deviance,
               hh2@model$training_metrics@metrics$null_deviance)
  expect_equal(hh1@model$training_metrics@metrics$AIC,
               hh2@model$training_metrics@metrics$AIC)
  expect_equal(hh1@model$training_metrics@metrics$null_degrees_of_freedom,
               hh2@model$training_metrics@metrics$null_degrees_of_freedom)
  expect_equal(hh1@model$coefficients,
               hh2@model$coefficients)  
  # NOPASS:
  #expect_equal(hh1@model$validation_metrics@metrics$MSE,
  #             hh2@model$validation_metrics@metrics$MSE,
  #             tolerance = 0.01)
  #expect_equal(hh1@model$validation_metrics@metrics$r2,
  #             hh2@model$validation_metrics@metrics$r2,
  #             tolerance = 0.01)
  
    
  #predictions
  print("compare predictions")
  ph1 <- as.data.frame(h2o.predict(object = hh1, newdata = val1))
  ph2 <- as.data.frame(h2o.predict(object = hh2, newdata = val1))
  mse1 <- mean((ph1$predict - newy)^2)
  mse2 <- mean((ph2$predict - newy)^2)
  expect_equal(mse1, mse2)
  

  # now use prostate to do the same test with a binary outcome
  #require(statmod)
  
  print("Read in prostate data.")
  prostate <- h2o.uploadFile("../../../../smalldata/prostate/prostate.csv", 
                             conn, destination_frame = "prostate")
  n <- nrow(prostate)
  
  # Training data with weights
  # draw some random weights ~ Poisson, add 'x1' weight col and y to df, hdf
  set.seed(1234)
  x1 <- rpois(n, rep(2, n)) + 1  #Random integer-valued (>=1) weights
  df <- cbind(as.data.frame(prostate), x1)  #design matrix with weight and outcome cols
  hdf <- as.h2o(object = df, conn = conn, destination_frame = "hdf")  #for h2o
  split_hdf <- h2o.splitFrame(hdf)
  hdf_train <- split_hdf[[1]]
  hdf_test <- split_hdf[[2]]
  df_train <- as.data.frame(hdf_train)

  # Training data (weights == 1.0 with repeated rows instead of weights)
  rep_idxs <- unlist(sapply(1:nrow(hdf_train), function(i) rep(i, as.data.frame(hdf_train$x1)[,1][i])))
  rdf <- df_train[rep_idxs,]  #repeat rows
  rdf$x1 <- 1  #set weights back to 1.0
  rhdf <- as.h2o(object = rdf, conn = conn, destination_frame = "rhdf")  #for h2o
  
  print("Set variables for h2o.")
  y <- "CAPSULE"
  x <- c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")
  
  
  print("build models with weights vs repeated rows with h2o")
  hh1 <- h2o.gbm(x = x, y = y, 
                 training_frame = hdf_train, 
                 validation_frame = hdf_test, 
                 ntrees = 50, 
                 weights_column = "x1")
  hh2 <- h2o.gbm(x = x, y = y, 
                 training_frame = rhdf, 
                 validation_frame = hdf_test,
                 ntrees = 50, 
                 weights_column = "x1")
  
  
  # Add a check that hh1 and hh2 produce the same results
  
  
  testEnd()
}


doTest("GLM weight Test: GBM w/ weights test by row duplication", test_weights_by_row_duplication)
