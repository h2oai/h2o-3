setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
source("../../../tests/runitUtils/utilsR.R")

# This test is written to make sure that when cross validation is enabled, the grid models
# are returned sorted by the cross-validation metrics and not the training metrics.  The
# following actions are performed:
#
# 1. generate a gridsearch model with GBM;
# 2. go into each gridsearch model, 
#   - grab the gridsearch logloss metrics, 
#   - generate cross validation logloss;
#   - generate the training logloss.
# 3. compare 
#   - the difference between the gridsearch logloss metrics and the generated cross validation logloss
#   - the difference between the gridsearch logloss metrics and the generated training logloss
# 4. If the first difference is small, second difference is small and the gridsearch logloss is sorted correctly,
#    declare test success.

test.GBM.gridsearch.sorting.metrics <- function() {
  
  test_failed = 1   # assume test failed to start
  
  df <- as.h2o(iris)
  
  ## Build 100 models using gridsearch
  grid <- h2o.grid(
    hyper_params = list(max_depth=c(1:10),ntrees=c(1:10)),
    search_criteria = list(strategy = "Cartesian"),
    algorithm="gbm", 
    nfolds=5, training_frame=df, x=1:4, y=5)
  
  grid_model_ids = grid@model_ids
  
  # go into each model, grab the xcross validation metrics and compare
  # with the one from grid model.  Grab and print out the training metrics
  # as well just to show that they are not the same.
  grid_model_index = 1
  grid_logloss_list = c()
  diff = 0
  diff_train = 0
  
  for (model_id in grid_model_ids) {
    each_model = h2o.getModel(model_id)
    
    ## grab each grid model's cross-validation logloss
    grid_logloss = as.numeric(grid@summary_table$logloss[grid_model_index])
    grid_logloss_list = c(grid_logloss_list, grid_logloss)
    
    ## generate each grid model's cross-validation logloss
    each_model_x_logloss = h2o.logloss(h2o.performance(each_model, xval=TRUE))
    
    # first difference, between grid logloss and generated cross-validation logloss
    diff = diff + abs(grid_logloss-each_model_x_logloss)
    
    ## generate training logloss of the grid model
    h2o.logloss(h2o.performance(each_model, train=TRUE))
    training_logloss = h2o.logloss(h2o.performance(each_model, newdata=df))
    
    # second difference, between grid logloss and generated training logloss
    diff_train = diff_train + abs(training_logloss-grid_logloss)
    
    Log.info(paste("grid model cross-validation logloss: ", grid_logloss))
    Log.info(paste("model training logloss: ", training_logloss))
    
    grid_model_index = grid_model_index+1
    
  }
  
  if ((diff < 1e-10) && (sum(grid_logloss_list == sort(grid_logloss_list)) == length(grid_logloss_list)) && (diff_train > 1e-10))
    test_failed = 0
  
  if (test_failed == 1) {
    throw("runit_GBM_gridsearch_sorting_metrics.R has failed.  I am sorry.")
  } else {
    Log.info("grid search models are sorted correctly by cross-validation metrics and not training metrics.")
  }
}

doTest("PUBDEV-2967.  Make sure gridsearch models are sorted by cross-validation metrics. ", test.GBM.gridsearch.sorting.metrics)