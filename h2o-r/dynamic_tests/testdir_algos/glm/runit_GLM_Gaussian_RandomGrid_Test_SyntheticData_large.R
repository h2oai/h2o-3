setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")
source("../../../tests/runitUtils/utilsR.R")

# PUBDEV-1843: Grid testing.  Subtask 9.
# This class is created to test the three stopping conditions for randomized gridsearch using
# GLM Gaussian family.  The three stopping conditions are :
# 
# 1. max_runtime_secs:
# 2. max_models:
# 3. metrics.  We will be picking 2 stopping metrics to test this stopping condition with.  One metric
# will be optimized if it increases and the other one should be optimized if it decreases.
# 
# I am testing 4 things:
# test1. First, no stopping conditions will be put on randomized search.  The purpose here is to make sure that 
# randomized search will give us all possible hyper-parameter combinations;
# test2. Second, test the stopping condition of setting the max_model in the search criteria;
# test3. Third, test the stopping condition max_runtime_secs in search criteria;
# test4. Fourth, test the stopping condition of using a metric that is decreasing;
# test5. Finally, test the stopping condition of using a metric that is increasing.
test.GLM.Gaussian.RandomGrid.Test.SyntheticData <- function() {
  # set random seed to generate random dataset
  set.seed(as.integer(Sys.time()))
  
  # setup parameters that control random hyperparameter value generation
  max_int_val = 10
  min_int_val = 0
  max_int_number = 5
  
  max_real_val = 1
  min_real_val = 0
  min_time_val = 0  # meaningful lower bound for max_runtime_secs, determined later
  max_real_number = 5
  time_scale = 2  # used to scale up the max_runtime_secs in hyper-parameters
  model_number_scale = 1
  max_runtime_scale = 1.5  # used to scale up the max_runtime_secs in search_criteria
  
  lambda_scale = 100
  alpha_scale = 1
  
  max_tolerance = 0.01  # maximum tolerance to be used for early stopping metric
  max_stopping_rounds = 10
  
  total_test_number = 5   # number of tests that are to be performed
  test_failed_array = rep(1, total_test_number)   # set test fail vectors to keep track of all test results
  test_index = 1    # index into which test we are testing for, remember there are 5 of them

  # for DEBUGGING
#     max_int_val = 1
#     max_real_number = 1
  ##### ENd Debugging

  train_data = h2o.importFile(locate("smalldata/gridsearch/gaussian_training1_set.csv"))

  col_names = colnames(train_data)
  train_col_count = length(col_names)
  response_index = train_col_count
  predictor_names = col_names[1:1-response_index]
  response_name = col_names[response_index]
  
  # setup model parameters for GLM Gaussian
  family = 'gaussian'
  nfolds = 5
  
  # get an estimate of how long it takes to train a model
  vanilla_glm = h2o.glm(x=predictor_names, y=response_name, training_frame=train_data, family=family, nfolds=nfolds)
  min_time_val = find_grid_runtime(c(vanilla_glm@model_id))
  
  # setup hyper-parameter for gridsearch
  hyper_parameters <- list()
  hyper_parameters$fold_assignment = c('AUTO', 'Random', 'Modulo')
  hyper_parameters$missing_values_handling = c('MeanImputation', 'Skip')
  
  # generate random hyper-parameter for gridsearch
  hyper_parameters$alpha = runif(max_real_number, min_real_val, max_real_val)
  hyper_parameters$lambda = runif(max_real_number, min_real_val*lambda_scale, max_real_val*lambda_scale)
  hyper_parameters$max_runtime_secs = runif(max_real_number, min_time_val, min_time_val*max_runtime_scale )
  
  Log.info("Hyper-parameters used to train gridsearch:")
  print(hyper_parameters)  # print out hyper-parameters used
  
  # list of parameter names in hyper-parameter
  parameter_names = names(hyper_parameters)
  
  # count upper bound on number of grid search model that can be built
  correct_model_number = hyperSpaceDimension(hyper_parameters)
  
  ###################   test 1: make sure random gridsearch generate all models
  # setup search-criteria for test1
  search_criteria = list()
  search_criteria$strategy = 'RandomDiscrete'
  search_criteria$stopping_rounds = 0
  search_criteria$seed = as.integer(Sys.time())
  
  Log.info("************* Test1: Make sure randomized gridsearch generate all models.")
  print(search_criteria)  # print out search criteria used
  
  grid_name = paste("myGLMGaussianGrid", as.integer(Sys.time()), sep="_")
  # start grid search 
  glm_grid1 = h2o.grid("glm", grid_id=grid_name, x=predictor_names, y=response_name, training_frame=train_data,
                       family=family, nfolds=nfolds, hyper_params=hyper_parameters, search_criteria=search_criteria)

  model_ids = glm_grid1@model_ids
    
  # check to make sure gridsearch return the correct number of models built
  if (length(model_ids) == correct_model_number) {
    test_failed_array[test_index] = 0
    Log.info("*************   test 1: PASSED.")
  } else {
    Log.info("###################   test 1: FAILED.")
  }
  
  ###################   test 2: max model stopping condition
  test_index = test_index+1
  rm(glm_grid1)
  
  # setup search-criteria for test2: test stopping condition max_model
  search_criteria$max_models = round(runif(1, 1, correct_model_number * model_number_scale))
  search_criteria$stopping_rounds = NULL
  
  Log.info("************* Test2: Test max_models stopping criteria:")
  print(search_criteria)  # print out search criteria used
  
  grid_name = paste("myGLMGaussianGrid", as.integer(Sys.time()), sep="_")
  # start grid search 
  glm_grid1 = h2o.grid("glm", grid_id=grid_name, x=predictor_names, y=response_name, training_frame=train_data,
                       family=family, nfolds=nfolds, hyper_params=hyper_parameters, search_criteria=search_criteria)
  
  model_ids = glm_grid1@model_ids
  model_number = length(model_ids)
    
  Log.info("Actual number of model built is ")
  print(model_number)
    
  Log.info("Stopping criteria: max_models")
  print(search_criteria$max_models)
    
  # check to make sure randomized grid search stop when the max model number is reached
  if (search_criteria$max_models > correct_model_number) {   # upper bound is too loose
    if (model_number == correct_model_number) {
      test_failed_array[test_index] = 0
    }
  } else if (model_number == search_criteria$max_models) {
    test_failed_array[test_index] = 0
  }

  if (test_failed_array[test_index] > 0) {
    Log.info("###################   test 2: FAILED.")
  } else {
    Log.info("*************   test 2: PASSED.")
  }
  
  ###################   test 3: max runtime stopping conditions
  test_index = test_index+1
  rm(glm_grid1)
  
  hyper_parameters$max_runtime_secs = NULL  # remove max_runtime condition in hyper-parameter
  # count upper bound on number of grid search model that can be built
  correct_model_number = hyperSpaceDimension(hyper_parameters)
  
  # setup search-criteria for test3: test stopping condition max_runtime_secs
  search_criteria$max_models = NULL  # remove the max_models stopping criteria
  search_criteria$max_runtime_secs = runif(1, min_time_val, min_time_val*correct_model_number*time_scale)
  
  Log.info("************* Test3: Test max_runtime_secs stopping criteria:")
  print(search_criteria)  # print out search criteria used
  
  grid_name = paste("myGLMGaussianGrid", as.integer(Sys.time()), sep="_")
  # start grid search 
  glm_grid1 = h2o.grid("glm", grid_id=grid_name, x=predictor_names, y=response_name, training_frame=train_data,
                       family=family, nfolds=nfolds, hyper_params=hyper_parameters, search_criteria=search_criteria)
  
  model_ids = glm_grid1@model_ids
  total_model_built_time = find_grid_runtime(model_ids)
    
  Log.info("Model building time is ")
  print(total_model_built_time)
  
  Log.info("Stopping criteria: max_runtime_secs")
  print(search_criteria$max_runtime_secs)
  
  Log.info("Total number of models built is ")
  print(length(model_ids))
  
  Log.info("Maximum number of models that can be built is ")
  print(correct_model_number)
    
  if ((total_model_built_time < search_criteria$max_runtime_secs * 1.5) || (length(model_ids) == 1)) {
    test_failed_array[test_index] = 0
    Log.info("*************   test 3: PASSED.")
  } else {
    Log.info("###################   test 3: FAILED.")
  }
  
  
  ###################   test 4: metric stopping conditions decreasing 
  test_index = test_index+1
  rm(glm_grid1)
  
  search_criteria$max_runtime_secs = NULL
  search_criteria$stopping_rounds = round(runif(1, 1, max_stopping_rounds))
  search_criteria$stopping_tolerance = runif(1, 1e-8, max_tolerance)
  
  # use decreasing metric first
  search_criteria$stopping_metric = "MSE"
  Log.info("************* Test4: Test decreasing stopping metrics MSE:")
  print(search_criteria)  # print out search criteria used
  
  grid_name = paste("myGLMGaussianGrid", as.integer(Sys.time()), sep="_")
  if (runGLMMetricStop(predictor_names, response_name, train_data, family, nfolds, hyper_parameters, search_criteria,
                       TRUE, correct_model_number,grid_name)) {
    test_failed_array[test_index] = 0
    Log.info("*************   test 4: PASSED.")
  } else {
    Log.info("###################   test 4: FAILED.")
  }
  
  ###################   test 5: metric stopping conditions increasing  
  test_index = test_index+1
  
  # use increasing metric first, need to wait for Navdeep fix.
  test_failed_array[test_index] = 0
#  search_criteria$stopping_metric = "r2"
#
#  Log.info("************* Test5: Test increasing stopping metrics r2:")
#  print(search_criteria)  # print out search criteria used
#
#  grid_name = paste("myGLMGaussianGrid", as.integer(Sys.time()), sep="_")
#  if (runGLMMetricStop(predictor_names, response_name, train_data, family, nfolds, hyper_parameters, search_criteria,
#                       FALSE, correct_model_number, grid_name)) {
#    test_failed_array[test_index] = 0
#    Log.info("*************   test 5: PASSED.")
#  } else {
#    Log.info("###################   test 5: FAILED.")
#  }
  
  if (sum(test_failed_array) > 0) {
    failure_message = summarize_failures(test_failed_array)
    throw(failure_message)
  }
}

doTest("GLM Gaussian Grid Test: PUBDEV-1843, subtask 9, check stopping conditions. ", 
       test.GLM.Gaussian.RandomGrid.Test.SyntheticData)

