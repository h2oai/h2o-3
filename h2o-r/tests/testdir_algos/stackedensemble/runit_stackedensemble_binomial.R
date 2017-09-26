setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.binomial.test <- function() {
  
  # This test checks the following (for binomial classification):
  # 
  # 1) That h2o.stackedEnsemble executes w/o errors 
  #    on a 2-model "manually constucted" ensemble.
  # 2) That h2o.predict works on a stack
  # 3) That h2o.performance works on a stack
  # 4) That the training and test performance is 
  #    better on a ensemble vs the base learners.
  # 5) That the validation_frame arg on 
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
    
  # Train a stacked ensemble using the GBM and RF above
  stack <- h2o.stackedEnsemble(x = x, 
                               y = y, 
                               training_frame = train,
                               validation_frame = test,  #also test that validation_frame is working
                               model_id = "my_ensemble_binomial", 
                               base_models = list(my_gbm@model_id, my_rf@model_id))
  
  # Check that prediction works
  pred <- h2o.predict(stack, newdata = test)
  expect_equal(nrow(pred), nrow(test))
  expect_equal(ncol(pred), 3)
  
  # Eval ensemble perf
  perf_stack_train <- h2o.performance(stack)
  perf_stack_test <- h2o.performance(stack, newdata = test)
  
  # Check that stack perf is better (bigger) than the best (biggest) base learner perf:
  # Training AUC
  baselearner_best_auc_train <- max(h2o.auc(perf_gbm_train), h2o.auc(perf_rf_train))
  stack_auc_train <- h2o.auc(perf_stack_train)
  print(sprintf("Best Base-learner Training AUC:  %s", baselearner_best_auc_train))
  print(sprintf("Ensemble Training AUC:  %s", stack_auc_train))
  expect_equal(TRUE,stack_auc_train > baselearner_best_auc_train)
  # Test AUC
  baselearner_best_auc_test <- max(h2o.auc(perf_gbm_test), h2o.auc(perf_rf_test))
  stack_auc_test <- h2o.auc(perf_stack_test)
  print(sprintf("Best Base-learner Test AUC:  %s", baselearner_best_auc_test))
  print(sprintf("Ensemble Test AUC:  %s", stack_auc_test))
  expect_equal(TRUE, stack_auc_test> baselearner_best_auc_test)
  
  # Check that passing `test` as a validation_frame
  # produces the same metrics as h2o.performance(stack, test)
  # Since the metrics object is not exactly the same, we can just test that AUC is the same
  perf_stack_validation_frame <- h2o.performance(stack, valid = TRUE)
  expect_identical(stack_auc_test, h2o.auc(perf_stack_validation_frame))
  
}

doTest("Stacked Ensemble Binomial Classification Test", stackedensemble.binomial.test)
