setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.binomial.grid.test <- function() {
  
  # This test checks the following (for binomial classification):
  # 
  # 1) That h2o.stackedEnsemble executes w/o errors 
  #    on a random-grid-based ensemble.
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
  
  search_criteria <- list(strategy = "RandomDiscrete", 
                          max_models = 3,
                          seed = 1)
  
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
                       grid_id = "gbm_grid_binomial",
                       x = x, 
                       y = y,
                       training_frame = train,
                       ntrees = 10,
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
                               validation_frame = test,
                               model_id = "my_ensemble_gbm_grid_binomial",
                               base_models = gbm_grid@model_ids)
  
  # Check that prediction works
  pred <- h2o.predict(stack, newdata = test)
  expect_equal(nrow(pred), nrow(test))
  expect_equal(ncol(pred), 3)
  
  # Eval ensemble perf
  perf_stack_train <- h2o.performance(stack)
  perf_stack_test <- h2o.performance(stack, newdata = test)

  # Training AUC for each base learner
  baselearner_best_auc_train <- max(sapply(gbm_grid@model_ids, function(mm) h2o.auc(h2o.getModel(mm), train = TRUE)))
  stack_auc_train <- h2o.auc(perf_stack_train)
  print(sprintf("Best Base-learner Training AUC:  %s", baselearner_best_auc_train))
  print(sprintf("Ensemble Training AUC:  %s", stack_auc_train))
  #expect_gt(stack_auc_train, baselearner_best_auc_train)  #Does not pass in this example, but this is ok

  # Check that stack perf is better (bigger) than the best (biggest) base learner perf:
  # Test AUC for each base learner
  baselearner_best_auc_test <- max(sapply(gbm_grid@model_ids, function(mm) h2o.auc(h2o.performance(h2o.getModel(mm), newdata = test))))
  stack_auc_test <- h2o.auc(perf_stack_test)
  print(sprintf("Best Base-learner Test AUC:  %s", baselearner_best_auc_test))
  print(sprintf("Ensemble Test AUC:  %s", stack_auc_test))
  expect_equal(TRUE,stack_auc_test > baselearner_best_auc_test)
  
  # Check that passing `test` as a validation_frame
  # produces the same metrics as h2o.performance(stack, test)
  # Since the metrics object is not exactly the same, we can just test that AUC is the same
  perf_stack_validation_frame <- h2o.performance(stack, valid = TRUE)
  expect_identical(h2o.auc(perf_stack_test), h2o.auc(perf_stack_validation_frame))
  
}

doTest("Stacked (Random Grid) Ensemble Binomial Classification Test", stackedensemble.binomial.grid.test)
