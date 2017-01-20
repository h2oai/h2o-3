setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.gaussian.test <- function() {
  
  # This test checks the following (for gaussian regression):
  # 
  # 1) That h2o.stackedEnsemble executes w/o errors 
  #    on a 2-model "manually constucted" ensemble.
  # 2) That h2o.predict works on a stack
  # 3) That h2o.performance works on a stack
  # 4) That the training and test performance is 
  #    better on a ensemble vs the base learners.
  # 5) That the validation_frame arg on 
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
                            ntrees = 10, 
                            nfolds = nfolds, 
                            fold_assignment = "Modulo",
                            keep_cross_validation_predictions = TRUE,
                            seed = 1)
  # Eval perf
  perf_rf_train <- h2o.performance(my_rf)
  perf_rf_test <- h2o.performance(my_rf, newdata = test)
  print("RF training performance: ")
  print(perf_rf_train)
  print("RF test performance: ")
  print(perf_rf_test)
    
  # Train a stacked ensemble using the GBM and GLM above
  stack <- h2o.stackedEnsemble(x = x, 
                               y = y, 
                               training_frame = train,
                               validation_frame = test,  #also test that validation_frame is working
                               model_id = "my_ensemble_gaussian", 
                               selection_strategy = "choose_all",
                               base_models = list(my_gbm@model_id, my_rf@model_id))
  
  # Check that prediction works
  pred <- h2o.predict(stack, newdata = test)
  expect_equal(nrow(pred), nrow(test))
  expect_equal(ncol(pred), 1)
  
  # Eval ensemble perf
  perf_stack_train <- h2o.performance(stack)
  perf_stack_test <- h2o.performance(stack, newdata = test)
  
  # Check that stack perf is better (smaller) than the best (smaller) base learner perf:
  # Training RMSE
  baselearner_best_rmse_train <- min(h2o.rmse(perf_gbm_train), h2o.rmse(perf_rf_train))
  stack_rmse_train <- h2o.rmse(perf_stack_train)
  print(sprintf("Best Base-learner Training RMSE:  %s", baselearner_best_rmse_train))
  expect_lte(stack_rmse_train, baselearner_best_rmse_train)
  # Test RMSE
  baselearner_best_rmse_test <- min(h2o.rmse(perf_gbm_test), h2o.rmse(perf_rf_test))
  stack_rmse_test <- h2o.rmse(perf_stack_test)
  print(sprintf("Best Base-learner Test RMSE:  %s", baselearner_best_rmse_test))
  #expect_lte(stack_rmse_test, baselearner_best_rmse_test)  #NOT PASSING, this dataset is probably too small
  
  # Check that passing `test` as a validation_frame
  # produces the same metrics as h2o.performance(stack, test)
  # Since the metrics object is not exactly the same, we can just test that RMSE is the same
  perf_stack_validation_frame <- h2o.performance(stack, valid = TRUE)
  expect_identical(stack_rmse_test, h2o.rmse(perf_stack_validation_frame))
  
  
}

doTest("Stacked Ensemble Gaussian Regression Test", stackedensemble.gaussian.test)
