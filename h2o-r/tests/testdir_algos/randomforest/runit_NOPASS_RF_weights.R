setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
### This tests observation weights in glm ######




test_weights_by_row_duplication <- function() {
  
  require(testthat)

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
  hdf$CAPSULE <- as.factor(hdf$CAPSULE)
  split_hdf <- h2o.splitFrame(hdf)
  hdf_train <- split_hdf[[1]]
  hdf_test <- split_hdf[[2]]
  df_train <- as.data.frame(hdf_train)

  tmp = as.data.frame(hdf_train$x1)[,1]
  # Training data (weights == 1.0 with repeated rows instead of weights)
  rep_idxs <- unlist(sapply(1:nrow(hdf_train), function(i) rep(i, tmp[i])))
  rdf <- df_train[rep_idxs,]  #repeat rows
  rdf$x1 <- 1  #set weights back to 1.0
  rhdf <- as.h2o(object = rdf, conn = conn, destination_frame = "rhdf")  #for h2o
  rhdf$CAPSULE <- as.factor(rhdf$CAPSULE)
  
  print("Set variables for h2o.")
  y <- "CAPSULE"
  x <- c("AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON")
  
  print("build models with weights vs repeated rows with h2o")
  hh1 <- h2o.randomForest(x = x, y = y, 
                 training_frame = hdf_train, 
                 validation_frame = hdf_test, 
                 ntrees = 50, 
                 weights_column = "x1",
                 seed = 1)
  hh2 <- h2o.randomForest(x = x, y = y, 
                 training_frame = rhdf, 
                 validation_frame = hdf_test,
                 ntrees = 50, 
                 weights_column = "x1",
                 seed = 1)
  
  # Add a check that hh1 and hh2 produce the same results
  
  expect_equal(hh1@model$training_metrics@metrics$MSE,
               hh2@model$training_metrics@metrics$MSE,
               tolerance = 1e-6)
  expect_equal(hh1@model$training_metrics@metrics$AUC,
               hh2@model$training_metrics@metrics$AUC,
               tolerance = 1e-6)
  expect_equal(hh1@model$training_metrics@metrics$logloss,
               hh2@model$training_metrics@metrics$logloss,
               tolerance = 1e-6)

  expect_equal(hh1@model$validation_metrics@metrics$MSE,
               hh2@model$validation_metrics@metrics$MSE,
               tolerance = 1e-6)
  expect_equal(hh1@model$validation_metrics@metrics$AUC,
               hh2@model$validation_metrics@metrics$AUC,
               tolerance = 1e-6)
  expect_equal(hh1@model$validation_metrics@metrics$logloss,
               hh2@model$validation_metrics@metrics$logloss,
               tolerance = 1e-6)
  
  
}


h2oTest.doTest("RF weight Test for bernoulli: RF w/ weights test by row duplication", test_weights_by_row_duplication)
