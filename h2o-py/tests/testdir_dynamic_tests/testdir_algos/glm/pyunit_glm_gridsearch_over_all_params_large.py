from __future__ import print_function

import sys
import random
import os
import numpy as np
import math
from builtins import range
import time
import json

sys.path.insert(1, "../../../../")

import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch


class Test_glm_grid_search:
    """
    PUBDEV-1843: Grid testing.  Subtask 7,8.

    This class is created to test the gridsearch with the GLM algo using Guassian, Binonmial or
    Multinomial family.  Three tests are written to test the following conditions:
    1. For hyper-parameters containing all legal parameter names and parameter value lists with legal
    and illegal values, grid-models should be built for all combinations of legal parameter values.  For
    illegal parameter values, a warning/error message should be printed out to warn the user but the
    program should not throw an exception;
    2. For hyper-parameters with illegal names, an exception should be thrown and no models should be built;
    3. For parameters that are specified both in the hyper-parameters and model parameters, unless the values
    specified in the model parameters are set to default values, an exception will be thrown since parameters are
    not supposed to be specified in both places.

    Test Descriptions:
    test_gbm_grid_search_over_params: test for condition 1 and performs the following:
        a. grab all truely griddable parameters and randomly or manually set the parameter values.
        b. Next, build H2O GLM models using grid search.  Count and make sure models
           are only built for hyper-parameters set to legal values.  No model is built for bad hyper-parameters
           values.  We should instead get a warning/error message printed out.
        c. For each model built using grid search, we will extract the parameters used in building
           that model and manually build a H2O GLM model.  MSEs are calculated from a test set
           to compare the performance of grid search model and our manually built model.  If their MSEs
           are close, declare test success.  Otherwise, declare test failure.
        d. we will check and make sure the models are built within the max_runtime_secs time limit that was set
           for it as well.  If max_runtime_secs was exceeded, declare test failure as well.

    test2_illegal_name_value: test for condition 1 and 2.  Randomly go into the hyper_parameters that we
    have specified, either
        a. randomly alter the name of a hyper-parameter name (fatal, exception will be thrown)
        b. randomly choose a hyper-parameter and remove all elements in its list (fatal)
        c. add randomly generated new hyper-parameter names with random list (fatal)
        d: randomly choose a hyper-parameter and insert an illegal type into it (non fatal, model built with
           legal hyper-parameters settings only and error messages printed out for illegal hyper-parameters
           settings)

    test3_duplicated_parameter_specification: test for condition 3.  Go into our hyper_parameters list, randomly
    choose some hyper-parameters to specify and specify it as part of the model parameters.  Hence, the same
    parameter is specified both in the model parameters and hyper-parameters.  Make sure the test failed with
    error messages when the parameter values are not set to default if they are specified in the model parameters
    as well as in the hyper-parameters.
    """

    # parameters set by users, change with care
    max_col_count = 4               # set maximum values of train/test row and column counts
    max_col_count_ratio = 300       # set max row count to be multiples of col_count to avoid over fitting
    min_col_count_ratio = 100       # set min row count to be multiples of col_count to avoid over fitting

    max_p_value = 2                 # set maximum predictor value
    min_p_value = -2                # set minimum predictor value

    max_w_value = 2                 # set maximum weight value
    min_w_value = -2                # set minimum weight value

    max_class_number = 10           # maximum number of classes allowed
    class_number = 2                # number of response class for classification, randomly determined later

    curr_time = str(round(time.time()))     # store current timestamp, used as part of filenames.

    # parameters denoting filenames of interested that store training/validation/test data sets in csv format
    training1_filename = "gridsearch_training1_"+curr_time+"_set.csv"
    training2_filename = "gridsearch_training2_"+curr_time+"_set.csv"

    json_filename = "gridsearch_hyper_parameter_" + curr_time + ".json"
    json_filename_bad = "gridsearch_hyper_parameter_bad_" + curr_time + ".json"

    weight_filename = "gridsearch_"+curr_time+"_weight.csv"

    allowed_diff = 1e-5   # value of p-values difference allowed between theoretical and h2o p-values

    # System parameters, do not change.  Dire consequences may follow if you do
    current_dir = os.path.dirname(os.path.realpath(sys.argv[1]))    # directory of this test file

    noise_std = 0.01            # noise variance in Gaussian noise generation added to response
    noise_var = noise_std*noise_std     # Gaussian noise variance

    train_row_count = 0         # training data row count, randomly generated later
    train_col_count = 0         # training data column count, randomly generated later

    # following parameters are used to generate hyper-parameters
    max_int_val = 10            # maximum size of random integer values
    min_int_val = -10           # minimum size of random integer values
    max_int_number = 5          # maximum number of integer random grid values to generate

    max_real_val = 1            # maximum size of random float values
    min_real_val = -1           # minimum size of random float values
    max_real_number = 5         # maximum number of real grid values to generate

    lambda_scale = 50           # scale the lambda values to be higher than 0 to 1
    alpha_scale = 1.2           # scale alpha into bad ranges
    time_scale = 3              # maximum runtime scale
    extra_time_fraction = 0.0   # since timing is never perfect, give some extra time on top of maximum runtime limit
    min_runtime_per_epoch = 0   # minimum run time found.  Determined later

    # parameters denoting filenames with absolute paths
    training1_data_file = os.path.join(current_dir, training1_filename)
    training2_data_file = os.path.join(current_dir, training2_filename)
    weight_data_file = os.path.join(current_dir, weight_filename)

    families = ['gaussian', 'binomial', 'multinomial']    # distribution family to perform grid search over
    family = 'gaussian'     # choose default family to be gaussian

    test_name = "pyunit_glm_gridsearch_over_all_params_large.py"     # name of this test
    sandbox_dir = ""  # sandbox directory where we are going to save our failed test data sets

    # store information about training/test data sets
    x_indices = []              # store predictor indices in the data set
    y_index = 0                 # store response index in the data set

    training1_data = []         # store training data sets
    training2_data = []         # store training data sets

    total_test_number = 3       # number of tests carried out
    test_failed = 0             # count total number of tests that have failed
    test_failed_array = [0]*total_test_number   # denote test results for all tests run.  1 error, 0 pass
    test_num = 0                # index representing which test is being run

    # give the user opportunity to pre-assign hyper parameters for fixed values
    hyper_params_bad = dict()
    hyper_params_bad["fold_assignment"] = ['AUTO', 'Random', 'Modulo']
    hyper_params_bad["missing_values_handling"] = ['MeanImputation', 'Skip']

    hyper_params = dict()
    hyper_params["fold_assignment"] = ['AUTO', 'Random', 'Modulo']
    hyper_params["missing_values_handling"] = ['MeanImputation', 'Skip']

    # parameters to be excluded from hyper parameter list even though they may be gridable
    exclude_parameter_lists = ['tweedie_link_power', 'tweedie_variance_power']   # do not need these

    # these are supposed to be gridable but are not really
    exclude_parameter_lists.extend(['fold_column', 'weights_column', 'offset_column'])

    # these are excluded for extracting parameters to manually build H2O GLM models
    exclude_parameter_lists.extend(['model_id'])

    gridable_parameters = []    # store griddable parameter names
    gridable_types = []         # store the corresponding griddable parameter types
    gridable_defaults = []      # store the gridabble parameter default values

    possible_number_models = 0      # possible number of models built based on hyper-parameter specification
    correct_model_number = 0        # count number of models built with bad hyper-parameter specification
    true_correct_model_number = 0   # count number of models built with good hyper-parameter specification
    nfolds = 5                      # enable cross validation to test fold_assignment

    def __init__(self):
        self.setup_data()
        self.setup_model()

    def setup_data(self):
        """
        This function performs all initializations necessary:
        1. generates all the random parameter values for our dynamic tests like the Gaussian
        noise std, column count and row count for training/test data sets.
        2. randomly choose the distribution family (gaussian, binomial, multinomial)
        to test.
        3. with the chosen distribution family, generate the appropriate data sets
        4. load the data sets and set the training set indices and response column index
        """

        # create and clean out the sandbox directory first
        self.sandbox_dir = pyunit_utils.make_Rsandbox_dir(self.current_dir, self.test_name, True)

        # randomly set Gaussian noise standard deviation as a fraction of actual predictor standard deviation
        self.noise_std = random.uniform(0, math.sqrt(pow((self.max_p_value - self.min_p_value), 2) / 12))
        self.noise_var = self.noise_std*self.noise_std

        # randomly determine data set size in terms of column and row counts
        self.train_col_count = random.randint(1, self.max_col_count)
        self.train_row_count = round(self.train_col_count * random.uniform(self.min_col_count_ratio,
                                                                           self.max_col_count_ratio))

        #  DEBUGGING setup_data, remember to comment them out once done.
        # self.train_col_count = 3
        # self.train_row_count = 200
        # self.max_real_number = 3
        # self.max_int_number = 3
        # end DEBUGGING

        # randomly choose which family of GLM algo to use
        self.family = self.families[random.randint(0, len(self.families)-1)]

        # set class number for classification
        if 'binomial' in self.family:
            self.class_number = 2
        elif 'multinomial' in self.family:
            self.class_number = random.randint(3, self.max_class_number)    # randomly set number of classes K

        # generate real value weight vector and training/validation/test data sets for GLM
        pyunit_utils.write_syn_floating_point_dataset_glm(self.training1_data_file, "",
                                                          self.training2_data_file, self.weight_data_file,
                                                          self.train_row_count, self.train_col_count, 2,
                                                          self.max_p_value, self.min_p_value, self.max_w_value,
                                                          self.min_w_value, self.noise_std, self.family,
                                                          self.train_row_count, self.train_row_count,
                                                          class_number=self.class_number,
                                                          class_method=['probability', 'probability',
                                                                        'probability'])

        # preload data sets
        self.training1_data = h2o.import_file(pyunit_utils.locate(self.training1_data_file))
        self.training2_data = h2o.import_file(pyunit_utils.locate(self.training2_data_file))

        # set data set indices for predictors and response
        self.y_index = self.training1_data.ncol-1
        self.x_indices = list(range(self.y_index))

        # set response to be categorical for classification tasks
        if ('binomial' in self.family) or ('multinomial' in self.family):
            self.training1_data[self.y_index] = self.training1_data[self.y_index].round().asfactor()

            # check to make sure all response classes are represented, otherwise, quit
            if self.training1_data[self.y_index].nlevels()[0] < self.class_number:
                print("Response classes are not represented in training dataset.")
                sys.exit(0)

            self.training2_data[self.y_index] = self.training2_data[self.y_index].round().asfactor()

        # save the training data files just in case the code crashed.
        pyunit_utils.remove_csv_files(self.current_dir, ".csv", action='copy', new_dir_path=self.sandbox_dir)

    def setup_model(self):
        """
        This function setup the gridsearch hyper-parameters that will be used later on:

        1. It will first try to grab all the parameters that are griddable and parameters used by GLM.
        2. It will find the intersection of parameters that are both griddable and used by GLM.
        3. There are several extra parameters that are used by GLM that are denoted as griddable but actually is not.
        These parameters have to be discovered manually and they These are captured in self.exclude_parameter_lists.
        4. We generate the gridsearch hyper-parameter.  For numerical parameters, we will generate those randomly.
        For enums, we will include all of them.

        :return: None
        """
        # build bare bone model to get all parameters
        model = H2OGeneralizedLinearEstimator(family=self.family)
        model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

        run_time = pyunit_utils.find_grid_runtime([model])  # find model train time
        print("Time taken to build a base barebone model is {0}".format(run_time))

        summary_list = model._model_json["output"]["model_summary"]
        num_iteration = summary_list.cell_values[0][summary_list.col_header.index('number_of_iterations')]

        if num_iteration == 0:
            self.min_runtime_per_epoch = run_time
        else:
            self.min_runtime_per_epoch = run_time/num_iteration

        # grab all gridable parameters and its type
        (self.gridable_parameters, self.gridable_types, self.gridable_defaults) = \
            pyunit_utils.get_gridables(model._model_json["parameters"])

        # randomly generate griddable parameters including values outside legal range, like setting alpha values to
        # be outside legal range of 0 and 1 and etc
        (self.hyper_params_bad, self.gridable_parameters, self.gridable_types, self.gridable_defaults) = \
            pyunit_utils.gen_grid_search(model.full_parameters.keys(), self.hyper_params_bad,
                                         self.exclude_parameter_lists,
                                         self.gridable_parameters, self.gridable_types, self.gridable_defaults,
                                         random.randint(1, self.max_int_number),
                                         self.max_int_val, self.min_int_val,
                                         random.randint(1, self.max_real_number),
                                         self.max_real_val*self.alpha_scale, self.min_real_val*self.alpha_scale)

        # scale the value of lambda parameters
        if "lambda" in list(self.hyper_params_bad):
            self.hyper_params_bad["lambda"] = [self.lambda_scale * x for x in self.hyper_params_bad["lambda"]]

        # scale the max_runtime_secs parameters
        time_scale = self.time_scale * run_time
        if "max_runtime_secs" in list(self.hyper_params_bad):
            self.hyper_params_bad["max_runtime_secs"] = [time_scale * x for x
                                                         in self.hyper_params_bad["max_runtime_secs"]]

        self.possible_number_models = pyunit_utils.count_models(self.hyper_params_bad)

        # calculate true possible_number_models and exclude the bad parameters since they will not
        # result in any models being built
        alpha_len = len(self.hyper_params_bad["alpha"])
        lambda_len = len(self.hyper_params_bad["lambda"])
        time_len = len(self.hyper_params_bad["max_runtime_secs"])
        len_good_alpha = len([x for x in self.hyper_params_bad["alpha"] if (x >= 0) and (x <= 1)])
        len_good_lambda = len([x for x in self.hyper_params_bad["lambda"] if (x >= 0)])
        len_good_time = len([x for x in self.hyper_params_bad["max_runtime_secs"] if (x >= 0)])

        self.possible_number_models = int(self.possible_number_models * len_good_alpha * len_good_lambda *
                                          len_good_time/ (alpha_len * lambda_len * time_len))

        # randomly generate griddable parameters with only good values
        (self.hyper_params, self.gridable_parameters, self.gridable_types, self.gridable_defaults) = \
            pyunit_utils.gen_grid_search(model.full_parameters.keys(), self.hyper_params, self.exclude_parameter_lists,
                                         self.gridable_parameters, self.gridable_types, self.gridable_defaults,
                                         random.randint(1, self.max_int_number),
                                         self.max_int_val, 0,
                                         random.randint(1, self.max_real_number),
                                         self.max_real_val, 0)

        self.true_correct_model_number = pyunit_utils.count_models(self.hyper_params)

        # scale the value of lambda parameters
        if "lambda" in list(self.hyper_params):
            self.hyper_params["lambda"] = [self.lambda_scale * x for x in self.hyper_params["lambda"]]

        # scale the max_runtime_secs parameters
        if "max_runtime_secs" in list(self.hyper_params):
            self.hyper_params["max_runtime_secs"] = [time_scale * x for x
                                                     in self.hyper_params["max_runtime_secs"]]

        # write out the hyper-parameters used into json files.
        pyunit_utils.write_hyper_parameters_json(self.current_dir, self.sandbox_dir, self.json_filename_bad,
                                                 self.hyper_params_bad)

        pyunit_utils.write_hyper_parameters_json(self.current_dir, self.sandbox_dir, self.json_filename,
                                                 self.hyper_params)

    def tear_down(self):
        """
        This function performs teardown after the dynamic test is completed.  If all tests
        passed, it will delete all data sets generated since they can be quite large.  It
        will move the training/validation/test data sets into a Rsandbox directory so that
        we can re-run the failed test.
        """

        if self.test_failed:    # some tests have failed.  Need to save data sets for later re-runs
            # create Rsandbox directory to keep data sets and weight information
            self.sandbox_dir = pyunit_utils.make_Rsandbox_dir(self.current_dir, self.test_name, True)

            # Do not want to save all data sets.  Only save data sets that are needed for failed tests
            pyunit_utils.move_files(self.sandbox_dir, self.training1_data_file, self.training1_filename)

            # write out the jenkins job info into log files.
            json_file = os.path.join(self.sandbox_dir, self.json_filename)

            with open(json_file,'wb') as test_file:
                json.dump(self.hyper_params, test_file)

        else:   # all tests have passed.  Delete sandbox if if was not wiped before
            pyunit_utils.make_Rsandbox_dir(self.current_dir, self.test_name, False)

        # remove any csv files left in test directory
        pyunit_utils.remove_csv_files(self.current_dir, ".csv")
        pyunit_utils.remove_csv_files(self.current_dir, ".json")

    def test1_glm_grid_search_over_params(self):
        """
        test_gbm_grid_search_over_params: test for condition 1 and performs the following:
        a. grab all truely griddable parameters and randomly or manually set the parameter values.
        b. Next, build H2O GLM models using grid search.  Count and make sure models
           are only built for hyper-parameters set to legal values.  No model is built for bad hyper-parameters
           values.  We should instead get a warning/error message printed out.
        c. For each model built using grid search, we will extract the parameters used in building
           that model and manually build a H2O GLM model.  MSEs are calculated from a test set
           to compare the performance of grid search model and our manually built model.  If their MSEs
           are close, declare test success.  Otherwise, declare test failure.
        d. we will check and make sure the models are built within the max_runtime_secs time limit that was set
           for it as well.  If max_runtime_secs was exceeded, declare test failure as well.
        """

        print("*******************************************************************************************")
        print("test_gbm_grid_search_over_params for GLM " + self.family)

        h2o.cluster_info()

        try:
            print("Hyper-parameters used here is {0}".format(self.hyper_params_bad))

            # start grid search
            grid_model = H2OGridSearch(H2OGeneralizedLinearEstimator(family=self.family, nfolds=self.nfolds),
                                       hyper_params=self.hyper_params_bad)
            grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

            self.correct_model_number = len(grid_model)     # store number of models built

            # check the total time taken to build grid search models
            total_gridsearch_runtime = pyunit_utils.find_grid_runtime(grid_model)

            # add parameters into params_dict.  Use this to manually build model
            params_dict = dict()
            params_dict["family"] = self.family
            params_dict["nfolds"] = self.nfolds
            total_run_time_limits = 0.0   # calculate upper bound of max_runtime_secs
            true_run_time_limits = 0.0
            manual_run_runtime = 0.0

            # compare MSE performance of model built by gridsearch with manually built model
            for each_model in grid_model:

                # grab parameters used by grid search and build a dict out of it
                params_list = pyunit_utils.extract_used_params(self.hyper_params_bad.keys(), each_model.params,
                                                               params_dict)

                # need to taken out max_runtime_secs from model parameters, it is now set in .train()
                if "max_runtime_secs" in params_list:
                    max_runtime = params_list["max_runtime_secs"]
                    del params_list["max_runtime_secs"]
                else:
                    max_runtime = 0

                manual_model = H2OGeneralizedLinearEstimator(**params_list)
                manual_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data,
                                   max_runtime_secs=max_runtime)

                # collect the time taken to manually built all models
                model_runtime = pyunit_utils.find_grid_runtime([manual_model])  # time taken to build this model
                manual_run_runtime += model_runtime

                summary_list = manual_model._model_json['output']['model_summary']
                iteration_num = summary_list.cell_values[0][summary_list.col_header.index('number_of_iterations')]

                if max_runtime > 0:
                    # shortest possible time it takes to build this model
                    if (max_runtime < self.min_runtime_per_epoch) or (iteration_num <= 1):
                        total_run_time_limits += model_runtime
                    else:
                        total_run_time_limits += max_runtime

                true_run_time_limits += max_runtime

                # compute and compare test metrics between the two models
                test_grid_model_metrics = each_model.model_performance(test_data=self.training2_data)
                test_manual_model_metrics = manual_model.model_performance(test_data=self.training2_data)

                # just compare the mse in this case within tolerance:
                if abs(test_grid_model_metrics.mse() - test_manual_model_metrics.mse()) > self.allowed_diff:
                    self.test_failed += 1             # count total number of tests that have failed
                    self.test_failed_array[self.test_num] += 1

                    print("test_gbm_grid_search_over_params for GLM failed: grid search model and manually "
                          "built H2O model differ too much in test MSE!")
                    break

            total_run_time_limits = max(total_run_time_limits, true_run_time_limits) * (1+self.extra_time_fraction)

            # make sure the correct number of models are built by gridsearch
            if not (self.correct_model_number == self.possible_number_models):  # wrong grid model number
                self.test_failed += 1
                self.test_failed_array[self.test_num] = 1
                print("test_gbm_grid_search_over_params for GLM failed: number of models built by gridsearch "
                    "does not equal to all possible combinations of hyper-parameters")

            # make sure the max_runtime_secs is working to restrict model built time
            if manual_run_runtime > total_run_time_limits:
                self.test_failed += 1
                self.test_failed_array[self.test_num] = 1
                print("test_gbm_grid_search_over_params for GLM failed: number of models built by gridsearch "
                      "does not equal to all possible combinations of hyper-parameters")

            self.test_num += 1

            if self.test_failed == 0:
                print("test_gbm_grid_search_over_params for GLM has passed!")
        except:
            if self.possible_number_models > 0:
                print("test_gbm_grid_search_over_params for GLM failed: exception was thrown for no reason.")

    def test2_illegal_name_value(self):
        """
        test2_illegal_name_value: test for condition 1 and 2.  Randomly go into the hyper_parameters that we
        have specified, either
        a. randomly alter the name of a hyper-parameter name (fatal, exception will be thrown)
        b. randomly choose a hyper-parameter and remove all elements in its list (fatal)
        c. add randomly generated new hyper-parameter names with random list (fatal)
        d: randomly choose a hyper-parameter and insert an illegal type into it (non fatal, model built with
           legal hyper-parameters settings only and error messages printed out for illegal hyper-parameters
           settings)

        The following error conditions will be created depending on the error_number generated:

        error_number = 0: randomly alter the name of a hyper-parameter name;
        error_number = 1: randomly choose a hyper-parameter and remove all elements in its list
        error_number = 2: add randomly generated new hyper-parameter names with random list
        error_number = 3: randomly choose a hyper-parameter and insert an illegal type into it

        :return: None
        """
        print("*******************************************************************************************")
        print("test2_illegal_name_value for GLM " + self.family)
        h2o.cluster_info()

        error_number = np.random.random_integers(0, 3, 1)   # randomly choose an error

        print("Test 2 error_number is {0}".format(error_number[0]))

        error_hyper_params = \
            pyunit_utils.insert_error_grid_search(self.hyper_params, self.gridable_parameters, self.gridable_types,
                                                  error_number[0])

        print("test2_illegal_name_value: the bad hyper-parameters are: ")
        print(error_hyper_params)

        # copied from Eric to catch execution run errors and not quit
        try:
            grid_model = H2OGridSearch(H2OGeneralizedLinearEstimator(family=self.family, nfolds=self.nfolds),
                                       hyper_params=error_hyper_params)
            grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

            if error_number[0] > 2:
                # grid search should not failed in this case and check number of models built.
                if not (len(grid_model) == self.true_correct_model_number):
                    self.test_failed += 1
                    self.test_failed_array[self.test_num] = 1
                    print("test2_illegal_name_value failed. Number of model generated is "
                          "incorrect.")
                else:
                    print("test2_illegal_name_value passed.")
            else:   # other errors should cause exceptions being thrown and if not, something is wrong.
                self.test_failed += 1
                self.test_failed_array[self.test_num] = 1
                print("test2_illegal_name_value failed: exception should have been thrown for illegal"
                      "parameter name or empty hyper-parameter parameter list but did not!")
        except:
            if (error_number[0] <= 2) and (error_number[0] >= 0):
                print("test2_illegal_name_value passed: exception is thrown for illegal parameter name or empty"
                  "hyper-parameter parameter list.")
            else:
                self.test_failed += 1
                self.test_failed_array[self.test_num] = 1
                print("test2_illegal_name_value failed: exception should not have been thrown but did!")

        self.test_num += 1

    def test3_duplicated_parameter_specification(self):
        """
        This function will randomly choose a parameter in hyper_parameters and specify it as a parameter in the
        model.  Depending on the random error_number generated, the following is being done to the model parameter
        and hyper-parameter:

        error_number = 0: set model parameter to be  a value in the hyper-parameter value list, should
        generate error;
        error_number = 1: set model parameter to be default value, should not generate error in this case;
        error_number = 2: make sure model parameter is not set to default and choose a value not in the
        hyper-parameter value list.

        :return: None
        """
        print("*******************************************************************************************")
        print("test3_duplicated_parameter_specification for GLM " + self.family)

        error_number = np.random.random_integers(0, 2, 1)   # randomly choose an error

        print("Test 3 error_number is {0}".format(error_number[0]))

        params_dict, error_hyper_params = \
            pyunit_utils.generate_redundant_parameters(self.hyper_params, self.gridable_parameters,
                                                       self.gridable_defaults, error_number[0])

        params_dict["family"] = self.family
        params_dict["nfolds"] = self.nfolds

        print("Your hyper-parameter dict is: ")
        print(error_hyper_params)
        print("Your model parameters are: ")
        print(params_dict)

        # copied from Eric to catch execution run errors and not quit
        try:
            if "max_runtime_secs" in list(params_dict):     # need to set max_runtime_secs when calling train
                max_runtime_secs = params_dict["max_runtime_secs"]
                del params_dict["max_runtime_secs"]

                grid_model = H2OGridSearch(H2OGeneralizedLinearEstimator(**params_dict),
                                           hyper_params=error_hyper_params)
                grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data,
                                 max_runtime_secs=max_runtime_secs)
            else:
                grid_model = H2OGridSearch(H2OGeneralizedLinearEstimator(**params_dict),
                                           hyper_params=error_hyper_params)
                grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

            # if error_number = 1, it is okay.  Else it should fail.
            if not (error_number[0] == 1):
                self.test_failed += 1
                self.test_failed_array[self.test_num] = 1
                print("test3_duplicated_parameter_specification failed: Java error exception should have been "
                      "thrown but did not!")
            else:
                print("test3_duplicated_parameter_specification passed: Java error exception should not have "
                      "been thrown and did not!")
        except:
            if error_number[0] == 1:
                self.test_failed += 1
                self.test_failed_array[self.test_num] = 1
                print("test3_duplicated_parameter_specification failed: Java error exception should not "
                      "have been thrown! ")
            else:
                print("test3_duplicated_parameter_specification passed: Java error exception should "
                      "have been thrown and did.")


def test_grid_search_for_glm_over_all_params():
    """
    Create and instantiate class and perform tests specified for GLM

    :return: None
    """
    test_glm_grid = Test_glm_grid_search()
    test_glm_grid.test1_glm_grid_search_over_params()
    test_glm_grid.test2_illegal_name_value()
    test_glm_grid.test3_duplicated_parameter_specification()
    sys.stdout.flush()

    if test_glm_grid.test_failed:  # exit with error if any tests have failed
        sys.exit(1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_grid_search_for_glm_over_all_params)
else:
    test_grid_search_for_glm_over_all_params()
