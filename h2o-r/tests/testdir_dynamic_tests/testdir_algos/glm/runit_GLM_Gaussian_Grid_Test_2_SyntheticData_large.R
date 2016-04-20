setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../../scripts/h2o-r-test-setup.R")
source("../../../runitUtils/utilsR.R")

#   This test is written to test the gridsearch according to PUBDEV-1843: subtask 7, 8.  Basically,
#   We need to perform the following two tests:
#   1. For all parameter names and values that are specified in the hyperparameters.
#       a. If an illegal name is specified, an exception should be thrown.
#       b. If a bad value is specified, a warning message will be printed in this case to warn the user
#          of the bad parameter type.  An exception should not be thrown in this case.  We do not want
#          to annoy and punish the users who have entered most name/value correctly but due to fat
#          fingers screw up a last value.
#   2. If a user specifies a parameter in both the hyper parameter and the model parameters, the parameter
#      value can be set in two ways:
#       a. the parameter is set to default value in the model parameter;
#       b. if the parameter is not set to default, an exception should be generated
#
#   This test performs test 2 using GLM with Gaussian distribution.  The following tasks are performed:
#   1. Random data set with random size is first generated;
#   2. For all hyper-parameters that are griddable, randomly set the parameter values
#   3. Among all hyper-parameters that are set, randomly choose some of them and specify their value in the model
#      parameter as well.  There are several ways to choose the duplicated parameter values:
#      a.  The parameter value is set to be default.  No exception will be thrown;
#      b.  The parameter value is set to will be set to be one of the values in the hyper-parameter list for that 
#         parameter.  An exception will be generated in this case.

test.GLM.Gaussian.Grid.Test2.SyntheticData <- function() {
  # set random seed to generate random dataset
  set.seed(as.integer(Sys.time()))
  
  # setup parameters that control dataset size
  max_col_count = 4
  max_col_count_ratio = 300
  min_col_count_ratio = 200
  
  max_predictor_value = 50
  min_predictor_value = -50
  
  max_weight_value = 50
  min_weight_value = -50
  
  # setup parameters that control random hyperparameter value generation
  max_int_val = 10
  min_int_val = -10
  max_int_number = 5
  
  max_real_val = 1.2
  min_real_val = -1.2
  max_real_number = 5
  
  lambda_scale = 100
  alpha_scale = 1.2
  time_scale = 0.1
  
  test_failed = 1   # set to 1 if test has failed for some reason, default to be bad
  
  # set data size and generate the dataset
  noise_std = runif(1, 0, sqrt((max_predictor_value-min_predictor_value)^2/12))
  train_col_count = round(runif(1, 1, max_col_count))
  train_row_count = train_col_count * round(runif(1, min_col_count_ratio, max_col_count_ratio))
  
  training_dataset = genRegressionData(train_col_count, train_row_count, max_weight_value, min_weight_value,
                                       max_predictor_value, min_predictor_value, noise_std)
  
  col_names = colnames(training_dataset)
  predictor_names = col_names[1:train_col_count]
  response_name = col_names[train_col_count+1]
  
  # convert R data frame to H2O dataframe
  train_data = as.h2o(training_dataset)
  
  # setup hyper-parameter for gridsearch
  hyper_parameters <- list()
  hyper_parameters$fold_assignment = c('AUTO', 'Random', 'Modulo')
  hyper_parameters$missing_values_handling = c('MeanImputation', 'Skip')
  
  # generate random hyper-parameter for gridsearch
  hyper_parameters$alpha = runif(max_real_number, min_real_val, max_real_val)
  hyper_parameters$lambda = runif(max_real_number, min_real_val*lambda_scale, max_real_val*lambda_scale)
  hyper_parameters$max_runtime_secs = runif(max_real_number, min_real_val*time_scale, max_real_val*time_scale)
  
  # count upper bound on number of grid search model that can be built
  correct_model_number = hyperSpaceDimension(hyper_parameters)
  
  # setup model parameters for GLM Gaussian
  family = 'gaussian'
  nfolds = 5
  
  # summary of hyper_parameters
  parameter_names = c("fold_assignment", "missing_values_handling", "alpha", "lambda", "max_runtime_secs")
  parameter_defaults = list("AUTO", "MeanImputation", NULL, NULL, 0)
  
  Log.info("Hyper-parameters used to train gridsearch:")
  Log.info(hyper_parameters)  # print out hyper-parameters used
  
  # introduce randomly more errors into hyper-parameter list
  error_number = round(runif(1, 0, 1))
  
  # need to take out the bad argument values like negative values or values exceeding 1
  if (error_number == 0) {
    alpha_length = length(hyper_parameters[['alpha']])
    good_alpha_length = sum(((hyper_parameters[['alpha']] >= 0) & (hyper_parameters[['alpha']] <= 1)))
    
    lambda_length = length(hyper_parameters[['lambda']])
    good_lambda_length = sum(hyper_parameters[['lambda']] >= 0) 
    
    time_length = length(hyper_parameters[['max_runtime_secs']])
    good_time_length = sum(hyper_parameters[['max_runtime_secs']] > 0)
    
    correct_model_number = correct_model_number*good_alpha_length*good_lambda_length*good_time_length/(alpha_length*lambda_length*time_length)
  }
  
  model_parameters = generateModelparameter(hyper_parameters, error_number, parameter_names, parameter_defaults)

  grid_name = paste("myGLMBinomialGrid", as.integer(Sys.time()), sep="_")
  argument_list = list(algorithm="glm", grid_id=grid_name, x=predictor_names, y=response_name, training_frame=train_data, family='gaussian', nfolds=nfolds, hyper_params=hyper_parameters)
  
  for (name in names(model_parameters)) {
    if (nchar(name) > 0) {
      argument_list[[name]] = model_parameters[[name]]
    }
  }
  
  # start grid search 
  try((glm_grid1 = do.call(h2o.grid, argument_list)))
 
  if (exists("glm_grid1")) {  # error introduced in hyper-parameter is not fatal
    model_ids = glm_grid1@model_ids
    
    # check to make sure gridsearch return the correct number of models built
    if (length(model_ids) == correct_model_number) {
      test_failed = 0
    }
  } else {  # fatal error is introduced here.
    if ((correct_model_number == 0) || (error_number == 1)) {
      # exceptions should have been thrown in this case and it did
      test_failed = 0
    }
  }
  
  if (test_failed == 1) {
    throw("runit_GLM_Gaussian_Grid_Test_1_SyntheticData.R has failed.  I am sorry.")
  }
}

#----------------------------------------------------------------------
# generateModelparameter will add model parameters from some of the parameters
# that are found in hyper_parameters.  Depending on the error_number, the
# parameter value is derived from two sources:
# error_number = 0: set parameter to default value
# error_number = 1: set parameter to a value chosen from the parameter list
#
# Parameters:  hyper_parameters -- list of structures that contains all hyper-parameter specifications
#              error_number -- Integer, denoting how to generate model parameter value
#              hyper_parameters_names -- list of String contains all parameter names in the hyper-parameters
#              parameter_defaults -- default values for parameters with names listed in hyper_parameters_names
#
# Returns:     model_parameters: list containing parameter name and value pairs.
#----------------------------------------------------------------------
generateModelparameter <- function(hyper_parameters, error_number, hyper_parameters_names, parameter_defaults) {
  parameter_number = length(hyper_parameters)
  
  number_of_errors = floor(runif(1, 1, parameter_number))   # number of parameters to screw up
  parameters_indices = unique(floor(runif(number_of_errors, 1, parameter_number)))
  
  model_parameters = list()
  
  for (parameter_index in parameters_indices) {
    param_name = hyper_parameters_names[parameter_index]   # name of hyper-parameter to change
    param_list = hyper_parameters[[param_name]]            # grab the parameter list for that parameter name
    
    model_parameters = c(model_parameters, param_name)
    if (error_number == 0) {  # set parameter value to default value
      # choose eligible bad parameter index
      
      model_parameters[[param_name]] = parameter_defaults[[parameter_index]]
    } else {  # choose parameter value to anything but default
      temp_list = setdiff(hyper_parameters[[param_name]], parameter_defaults[[parameter_index]])
      random_index = floor(runif(1, 1, length(temp_list)))
      model_parameters[[param_name]] = temp_list[random_index]
    }
  }
  
  return(model_parameters)
}

doTest("GLM Gaussian Grid Test: PUBDEV-1843, subtask 8, test 2.  Check for redundant parameter specification.",
       test.GLM.Gaussian.Grid.Test2.SyntheticData)

