setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.binomial.test <- function() {
  
  # This test checks the following:
  # 
  # 1) That h2o.stackedEnsemble executes w/o errors on a 
  #    2-model "manually constucted" ensemble.
  # 2) That the training and test error are smaller on the 
  #    ensemble vs the base learners.
  # 3) TO DO: That the validation_frame arg on 
  #    h2o.stackedEnsemble works correctly    
  
  train <- h2o.uploadFile(locate("smalldata/testng/higgs_train_5k.csv"), 
                          destination_frame = "higgs_train_5k")
  test <- h2o.uploadFile(locate("smalldata/testng/higgs_test_5k.csv"), 
                         destination_frame = "higgs_test_5k")
  print(summary(train))
  y <- "response"
  x <- setdiff(names(train), y)
  train[,y] <- as.factor(train[,y])
  test[,y] <- as.factor(test[,y])
  nfolds <- 5
  
  # Train & Cross-validate a GBM
  my_gbm <- h2o.gbm(x = x, 
                    y = y, 
                    training_frame = train, 
                    distribution = "bernoulli",
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
  print("RF training performance: ")
  print(perf_rf_train)
  print("RF test performance: ")
  print(perf_rf_test)
    
  # Train a stacked ensemble using the GBM and GLM above
  stack <- h2o.stackedEnsemble(x = x, 
                               y = y, 
                               training_frame = train,
                               #validation_frame = test,  #also test that validation_frame is working
                               model_id = "my_ensemble_binomial", 
                               selection_strategy = "choose_all",
                               base_models = list(my_gbm@model_id, my_rf@model_id))
  
  # Check that prediction works
  pred <- h2o.predict(stack, newdata = train)  #works on train
  #pred <- h2o.predict(stack, newdata = test)  #but not test
  # Error: java.lang.IllegalArgumentException: Can not make vectors of different length compatible!
  #expect_equal(nrow(pred), 5000)
  #expect_equal(ncol(pred), 3)
  
  # Eval ensemble perf
  perf_stack_train <- h2o.performance(stack)
  #perf_stack_test <- h2o.performance(stack, newdata = test)  #ERROR!!
  # Error in Filter(function(mm) { : subscript out of bounds
  
  # Check that stack perf is better (bigger) than the best (biggest) base learner perf:
  # Training AUC
  expect_gte(h2o.auc(perf_stack_train), max(h2o.auc(perf_gbm_train), h2o.auc(perf_rf_train)))
  # Test AUC
  expect_gte(h2o.auc(perf_stack_test), max(h2o.auc(perf_gbm_test), h2o.auc(perf_rf_test)))
  
  # TO DO: Check that passing `test` as a validation_frame
  #        produces the same metrics as h2o.performance(stack, test)
  
  
}

doTest("Stacked Ensemble Test", stackedensemble.gaussian.test)
