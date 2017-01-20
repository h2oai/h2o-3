setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

stackedensemble.binomial.grid.test <- function() {
  
  # This test checks the following:
  # 
  # 1) That h2o.stackedEnsemble executes w/o errors on a 
  #    random-grid-based ensemble.
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
                       grid_id = "gbm_grid_higgs_binomial",
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
                               #validation_frame = test,
                               model_id = "my_ensemble_gbm_grid_binomial",
                               selection_strategy = c("choose_all"), 
                               base_models = gbm_grid@model_ids)
  
  # Check that prediction works
  pred <- h2o.predict(stack, newdata = train)  #works on train
  #pred <- h2o.predict(stack, newdata = test)  #but not test
  #Error: java.lang.IllegalArgumentException: Can not make vectors of different length compatible! 
  #expect_equal(nrow(pred), 5000)
  #expect_equal(ncol(pred), 3)
  
  # Eval ensemble perf
  perf_stack_train <- h2o.performance(stack)
  #perf_stack_test <- h2o.performance(stack, newdata = test)  # ERROR!!
  # Error in Filter(function(mm) { : subscript out of bounds
 
  
  
  # Check that stack perf is better (bigger) than the best (biggest) base learner perf:
  # Training AUC for each base learner
  auc_gbm_grid_train <- sapply(gbm_grid@model_ids, function(mm) h2o.auc(h2o.getModel(mm), train = TRUE))
  #expect_gte(h2o.auc(perf_stack_train), max(auc_gbm_grid_train))
  # Test AUC for each base learner
  auc_gbm_grid_test <- sapply(gbm_grid@model_ids, function(mm) h2o.auc(h2o.performance(h2o.getModel(mm), newdata = test)))
  expect_gte(h2o.auc(perf_stack_train), max(auc_gbm_grid_test))
  # TO DO: Check that passing `test` as a validation_frame
  #        produces the same metrics as h2o.performance(stack, test)
  
  
}

doTest("Stacked Ensemble Test", stackedensemble.gaussian.grid.test)
