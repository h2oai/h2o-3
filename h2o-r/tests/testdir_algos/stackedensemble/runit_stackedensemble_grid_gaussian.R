setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.gaussian.grid.test <- function() {
  
  # This test checks the following:
  # 
  # 1) That h2o.stackedEnsemble executes w/o errors on a 
  #    random-grid-based ensemble.
  # 2) That the training and test error are smaller on the 
  #    ensemble vs the base learners.
  # 3) TO DO: That the validation_frame arg on 
  #    h2o.stackedEnsemble works correctly    
  
  dat <- h2o.uploadFile(locate("smalldata/extdata/australia.csv"), 
                        destination_frame = "australia.hex")
  ss <- h2o.splitFrame(dat, seed = 1)
  train <- ss[[1]]
  test <- ss[[2]]
  print(summary(train))
  x <- c("premax", "salmax", "minairtemp", "maxairtemp", "maxsst", "maxsoilmoist", "Max_czcs")
  y <- "runoffnew"
  nfolds <- 5
  
  search_criteria <- list(strategy = "RandomDiscrete", 
                          max_models = 4,
                          seed = 1)
  nfolds <- 5
  
  # GBM Hyperparamters
  learn_rate_opt <- c(0.01, 0.03) 
  max_depth_opt <- c(3, 4, 5, 6, 9)
  sample_rate_opt <- c(0.7, 0.8, 0.9, 1.0)
  col_sample_rate_opt <- c(0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8)
  hyper_params <- list(learn_rate = learn_rate_opt,
                       max_depth = max_depth_opt, 
                       sample_rate = sample_rate_opt,
                       col_sample_rate = col_sample_rate_opt)
  
  gbm_grid <- h2o.grid(algorithm = "gbm", 
                       grid_id = "gbm_grid",
                       x = x, 
                       y = y,
                       training_frame = train,
                       ntrees = 50,
                       seed = 1,
                       nfolds = nfolds,
                       fold_assignment = "Modulo",
                       keep_cross_validation_predictions = TRUE,
                       hyper_params = hyper_params,
                       search_criteria = search_criteria)
  
  # Train a stacked ensemble using the GBM grid
  stack <- h2o.stackedEnsemble(x = x, 
                               y = y, 
                               training_frame = train, 
                               model_id = "my_ensemble_gbm_grid",
                               selection_strategy = c("choose_all"), 
                               base_models = gbm_grid@model_ids)
  
  # Check that prediction works
  #pred <- h2o.predict(stack, newdata = test)
  #Error: java.lang.IllegalArgumentException: Can not make vectors of different length compatible! 
  #expect_equal(nrow(pred), 5000)
  #expect_equal(ncol(pred), 1)
  
  # Eval ensemble perf
  perf_stack_train <- h2o.performance(stack)
  perf_stack_test <- h2o.performance(stack, newdata = test)  # ERROR!!
  # ERROR MESSAGE:
  #   
  # Can not make vectors of different length compatible! 
  
  
  # Check that stack perf is better (smaller) than the best (smallest) base learner perf:
  # Training error
  #expect_lte(h2o.rmse(perf_stack_train), min(h2o.rmse(perf_gbm_train), h2o.rmse(perf_rf_train)))
  # Test error
  #expect_lte(h2o.rmse(perf_stack_test), min(h2o.rmse(perf_gbm_test), h2o.rmse(perf_rf_test)))
  
  # TO DO: Check that passing `test` as a validation_frame
  #        produces the same metrics as h2o.performance(stack, test)
  
  
}

doTest("Stacked Ensemble Test", stackedensemble.gaussian.grid.test)
