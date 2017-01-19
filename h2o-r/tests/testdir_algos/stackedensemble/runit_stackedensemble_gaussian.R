setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.gaussian.test <- function() {
  
  # This test checks the following:
  # 
  # 1) That h2o.stackedEnsemble executes w/o errors on a 
  #    2-model "manually constucted" ensemble.
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
  
  # Train & Cross-validate a GBM
  my_gbm <- h2o.gbm(x = x, 
                    y = y, 
                    training_frame = train, 
                    distribution = "gaussian",
                    ntrees = 10, 
                    max_depth = 3,
                    min_rows = 2, 
                    learn_rate = 0.2, 
                    nfolds = nfolds, 
                    fold_assignment = "Modulo",
                    keep_cross_validation_predictions = TRUE,
                    seed = 1)
  # Eval perf
  perf_gbm_train <- h2o.performance(my_gbm)
  perf_gbm_test <- h2o.performance(my_gbm, newdata = test)
  print("GBM training performance: ")
  print(perf_gbm_train)
  print("GBM test performance: ")
  print(perf_gbm_test)
  
  
  # Train & Cross-validate a RF
  my_rf <- h2o.randomForest(x = x,
                            y = y, 
                            training_frame = train, 
                            ntrees = 50, 
                            nfolds = nfolds, 
                            fold_assignment = "Modulo",
                            keep_cross_validation_predictions = TRUE,
                            seed = 1)
  # Eval perf
  perf_rf_train <- h2o.performance(my_rf)
  perf_rf_test <- h2o.performance(my_rf, newdata = test)
  print("GBM training performance: ")
  print(perf_rf_train)
  print("GBM test performance: ")
  print(perf_rf_test)
    
  # Train a stacked ensemble using the GBM and GLM above
  stack <- h2o.stackedEnsemble(x = x, 
                               y = y, 
                               training_frame = train,
                               #validation_frame = test,  #also test that validation_frame is working
                               model_id = "my_ensemble", 
                               selection_strategy = "choose_all",
                               base_models = list(my_gbm@model_id, my_rf@model_id))
  # Eval ensemble perf
  perf_stack_train <- h2o.performance(stack)
  perf_stack_test <- h2o.performance(stack, newdata = test)
  
  
  # Check that stack perf is better (smaller) than the best (smallest) base learner perf:
  # Training error
  expect_lte(h2o.rmse(perf_stack_train), min(h2o.rmse(perf_gbm_train), h2o.rmse(perf_rf_train)))
  # Test error
  expect_lte(h2o.rmse(perf_stack_test), min(h2o.rmse(perf_gbm_test), h2o.rmse(perf_rf_test)))
  
  # TO DO: Check that passing `test` as a validation_frame
  #        produces the same metrics as h2o.performance(stack, test)
  
  
}

doTest("Stacked Ensemble Test", stackedensemble.gaussian.test)
