from __future__ import print_function

import sys
import random
import os
from builtins import range
import time
import json

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch


class Test_glm_random_grid_search:
    """
    This class is created to test the three stopping conditions for randomized gridsearch using
    GLM Binomial family.  The three stopping conditions are :

    1. max_runtime_secs:
    2. max_models:
    3. metrics.  We will be picking 2 stopping metrics to test this stopping condition with.  One metric
    will be optimized if it increases and the other one should be optimized if it decreases.

    I have written 4 tests:
    1. test1_glm_random_grid_search_model_number: this test will not put any stopping conditions
    on randomized search.  The purpose here is to make sure that randomized search will give us all possible
    hyper-parameter combinations.
    2. test2_glm_random_grid_search_max_model: this test the stopping condition of setting the max_model in
    search criteria;
    3. test3_glm_random_grid_search_max_runtime_secs: this test the stopping condition max_runtime_secs
    in search criteria;
    4. test4_glm_random_grid_search_metric: this test the stopping condition of using a metric which can be
    increasing or decreasing.
    """
    # parameters set by users, change with care
    curr_time = str(round(time.time()))

    # parameters denoting filenames of interested that store training/validation/test data sets in csv format
    training1_filename = "smalldata/gridsearch/binomial_training1_set.csv"
    json_filename = "random_gridsearch_GLM_binomial_hyper_parameter_" + curr_time + ".json"

    allowed_diff = 0.5   # error tolerance allowed
    allowed_time_diff = 1e-1    # fraction of max_runtime_secs allowed for max run time stopping criteria

    # System parameters, do not change.  Dire consequences may follow if you do
    current_dir = os.path.dirname(os.path.realpath(sys.argv[1]))    # directory of this test file

    train_row_count = 0         # training data row count, randomly generated later
    train_col_count = 0         # training data column count, randomly generated later

    max_int_val = 1000          # maximum size of random integer values
    min_int_val = 0             # minimum size of random integer values
    max_int_number = 3          # maximum number of integer random grid values to generate

    max_real_val = 1            # maximum size of random float values
    min_real_val = 0.0          # minimum size of random float values
    max_real_number = 3         # maximum number of real grid values to generate

    lambda_scale = 100          # scale lambda value to be from 0 to 100 instead of 0 to 1
    max_runtime_scale = 3       # scale the max runtime to be different from 0 to 1

    one_model_time = 0          # time taken to build one barebone model

    possible_number_models = 0      # possible number of models built based on hyper-parameter specification
    max_model_number = 0    # maximum number of models specified to test for stopping conditions, generated later
    max_grid_runtime = 1          # maximum runtime value in seconds, 1 minute max
    allowed_scaled_overtime = 1   # used to set max_allowed_runtime as allowed_scaled_overtime * total model run time
    allowed_scaled_time = 1       # scale back time
    allowed_scaled_model_number = 1.5   # used to set max_model_number as
    # possible_number_models * allowed_scaled_model_number
    max_stopping_rounds = 5            # maximum stopping rounds allowed to be used for early stopping metric
    max_tolerance = 0.01                # maximum tolerance to be used for early stopping metric

    family = 'binomial'     # set gaussian as default

    test_name = "pyunit_glm_binomial_gridsearch_randomdiscrete_large.py"     # name of this test
    sandbox_dir = ""  # sandbox directory where we are going to save our failed test data sets

    # store information about training/test data sets
    x_indices = []      # store predictor indices in the data set
    y_index = 0         # store response index in the data set

    training1_data = []  # store training data sets

    total_test_number = 5       # number of tests carried out
    test_failed = 0             # count total number of tests that have failed
    test_failed_array = [0]*total_test_number   # denote test results for all tests run.  1 error, 0 pass
    test_num = 0                # index representing which test is being run

    # give the user opportunity to pre-assign hyper parameters for fixed values
    hyper_params = {}

    # parameters to be excluded from hyper parameter list even though they may be gridable
    exclude_parameter_lists = ['tweedie_link_power', 'tweedie_variance_power']   # do not need these

    # these are supposed to be gridable but not really
    exclude_parameter_lists.extend(['fold_column', 'weights_column', 'offset_column'])

    # these are excluded for extracting parameters to manually build H2O GLM models
    exclude_parameter_lists.extend(['model_id'])

    gridable_parameters = []    # store griddable parameter names
    gridable_types = []         # store the corresponding griddable parameter types
    gridable_defaults = []      # store the gridabble parameter default values

    correct_model_number = 0    # count number of models built with correct hyper-parameter specification
    nfolds = 5                  # enable cross validation to test fold_assignment

    def __init__(self, family):
        """
        Constructor.

        :param family: distribution family for tests
        :return: None
        """
        self.setup_data()       # setup_data training data
        self.setup_grid_params()    # setup_data grid hyper-parameters

    def setup_data(self):
        """
        This function performs all initializations necessary:
        load the data sets and set the training set indices and response column index
        """

        # clean out the sandbox directory first
        self.sandbox_dir = pyunit_utils.make_Rsandbox_dir(self.current_dir, self.test_name, True)

        # preload data sets
        self.training1_data = h2o.import_file(path=pyunit_utils.locate(self.training1_filename))

        # set data set indices for predictors and response
        self.y_index = self.training1_data.ncol-1
        self.x_indices = list(range(self.y_index))
        self.training1_data[self.y_index] = self.training1_data[self.y_index].round().asfactor()

        # save the training data files just in case the code crashed.
        pyunit_utils.remove_csv_files(self.current_dir, ".csv", action='copy', new_dir_path=self.sandbox_dir)

    def setup_grid_params(self):
        """
        This function setup the randomized gridsearch parameters that will be used later on:

        1. It will first try to grab all the parameters that are griddable and parameters used by GLM.
        2. It will find the intersection of parameters that are both griddable and used by GLM.
        3. There are several extra parameters that are used by GLM that are denoted as griddable but actually is not.
        These parameters have to be discovered manually and they These are captured in self.exclude_parameter_lists.
        4. We generate the gridsearch hyper-parameter.  For numerical parameters, we will generate those randomly.
        For enums, we will include all of them.

        :return: None
        """
        # build bare bone model to get all parameters
        model = H2OGeneralizedLinearEstimator(family=self.family, nfolds=self.nfolds)
        model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

        self.one_model_time = pyunit_utils.find_grid_runtime([model])  # find model train time
        print("Time taken to build a base barebone model is {0}".format(self.one_model_time))

        # grab all gridable parameters and its type
        (self.gridable_parameters, self.gridable_types, self.gridable_defaults) = \
            pyunit_utils.get_gridables(model._model_json["parameters"])

        # give the user opportunity to pre-assign hyper parameters for fixed values
        self.hyper_params = {}
        self.hyper_params["fold_assignment"] = ['AUTO', 'Random', 'Modulo', "Stratified"]
        self.hyper_params["missing_values_handling"] = ['MeanImputation', 'Skip']

        # randomly generate griddable parameters
        (self.hyper_params, self.gridable_parameters, self.gridable_types, self.gridable_defaults) = \
            pyunit_utils.gen_grid_search(model.full_parameters.keys(), self.hyper_params, self.exclude_parameter_lists,
                                         self.gridable_parameters, self.gridable_types, self.gridable_defaults,
                                         random.randint(1, self.max_int_number), self.max_int_val, self.min_int_val,
                                         random.randint(1, self.max_real_number), self.max_real_val, self.min_real_val)

        # change the value of lambda parameters to be from 0 to self.lambda_scale instead of 0 to 1.
        if "lambda" in list(self.hyper_params):
            self.hyper_params["lambda"] = [self.lambda_scale * x for x in self.hyper_params["lambda"]]

        time_scale = self.max_runtime_scale * self.one_model_time
            # change the value of runtime parameters to be from 0 to self.lambda_scale instead of 0 to 1.
        if "max_runtime_secs" in list(self.hyper_params):
            self.hyper_params["max_runtime_secs"] = [time_scale * x for x in
                                                     self.hyper_params["max_runtime_secs"]]

        # number of possible models being built:
        self.possible_number_models = pyunit_utils.count_models(self.hyper_params)

        # save hyper-parameters in sandbox and current test directories.
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

    def test1_glm_random_grid_search_model_number(self, metric_name):
        """
        This test is used to make sure the randomized gridsearch will generate all models specified in the
        hyperparameters if no stopping condition is given in the search criterion.

        :param metric_name: string to denote what grid search model should be sort by

        :return: None
        """
        print("*******************************************************************************************")
        print("test1_glm_random_grid_search_model_number for GLM " + self.family)
        h2o.cluster_info()

        # setup_data our stopping condition here, random discrete and find all models
        search_criteria = {'strategy': 'RandomDiscrete', "stopping_rounds": 0, "seed": int(round(time.time()))}
        print("GLM Binomial grid search_criteria: {0}".format(search_criteria))

        # fire off random grid-search
        random_grid_model = \
            H2OGridSearch(H2OGeneralizedLinearEstimator(family=self.family, nfolds=self.nfolds),
                          hyper_params=self.hyper_params, search_criteria=search_criteria)
        random_grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

        # compare number of models built from both gridsearch
        if not (len(random_grid_model) == self.possible_number_models):
            self.test_failed += 1
            self.test_failed_array[self.test_num] = 1
            print("test1_glm_random_grid_search_model_number for GLM: failed, number of models generated"
                  "possible model number {0} and randomized gridsearch model number {1} are not "
                  "equal.".format(self.possible_number_models, len(random_grid_model)))
        else:
            self.max_grid_runtime = pyunit_utils.find_grid_runtime(random_grid_model)   # time taken to build all models

        if self.test_failed_array[self.test_num] == 0:
            print("test1_glm_random_grid_search_model_number for GLM: passed!")

        self.test_num += 1
        sys.stdout.flush()

    def test2_glm_random_grid_search_max_model(self):
        """
        This test is used to test the stopping condition max_model_number in the randomized gridsearch.  The
        max_models parameter is randomly generated.  If it is higher than the actual possible number of models
        that can be generated with the current hyper-space parameters, randomized grid search should generate
        all the models.  Otherwise, grid search shall return a model that equals to the max_model setting.
        """
        print("*******************************************************************************************")
        print("test2_glm_random_grid_search_max_model for GLM " + self.family)
        h2o.cluster_info()

        # setup_data our stopping condition here
        self.max_model_number = random.randint(1, int(self.allowed_scaled_model_number * self.possible_number_models))
        search_criteria = {'strategy': 'RandomDiscrete', 'max_models': self.max_model_number,
                           "seed": int(round(time.time()))}

        print("GLM Binomial grid search_criteria: {0}".format(search_criteria))
        print("Possible number of models built is {0}".format(self.possible_number_models))

        # fire off random grid-search
        grid_model = \
            H2OGridSearch(H2OGeneralizedLinearEstimator(family=self.family, nfolds=self.nfolds),
                          hyper_params=self.hyper_params, search_criteria=search_criteria)
        grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

        number_model_built = len(grid_model)    # count actual number of models built

        print("Maximum model limit is {0}.  Number of models built is {1}".format(search_criteria["max_models"],
                                                                                  number_model_built))

        if self.possible_number_models >= self.max_model_number:    # stopping condition restricts model number
            if not (number_model_built == self.max_model_number):
                print("test2_glm_random_grid_search_max_model: failed.  Number of model built {0} "
                      "does not match stopping condition number{1}.".format(number_model_built, self.max_model_number))
                self.test_failed += 1
                self.test_failed_array[self.test_num] = 1
            else:
                print("test2_glm_random_grid_search_max_model for GLM: passed.")
        else:   # stopping condition is too loose
            if not (number_model_built == self.possible_number_models):
                self.test_failed += 1
                self.test_failed_array[self.test_num] = 1
                print("test2_glm_random_grid_search_max_model: failed. Number of model built {0} does not equal "
                      "to possible model number {1}.".format(number_model_built, self.possible_number_models))
            else:
                print("test2_glm_random_grid_search_max_model for GLM: passed.")

        self.test_num += 1
        sys.stdout.flush()

    def test3_glm_random_grid_search_max_runtime_secs(self):
        """
        This function will test the stopping criteria max_runtime_secs.  For each model built, the field
        run_time actually denote the time in ms used to build the model.  We will add up the run_time from all
        models and check against the stopping criteria max_runtime_secs.  Since each model will check its run time
        differently, there is some inaccuracies in the actual run time.  For example, if we give a model 10 ms to
        build.  The GLM may check and see if it has used up all the time for every 10 epochs that it has run.  On
        the other hand, deeplearning may check the time it has spent after every epoch of training.

        If we are able to restrict the runtime to not exceed the specified max_runtime_secs by a certain
        percentage, we will consider the test a success.

        :return: None
        """
        print("*******************************************************************************************")
        print("test3_glm_random_grid_search_max_runtime_secs for GLM " + self.family)
        h2o.cluster_info()

        if "max_runtime_secs" in list(self.hyper_params):
            del self.hyper_params['max_runtime_secs']
            # number of possible models being built:
            self.possible_number_models = pyunit_utils.count_models(self.hyper_params)

        # setup_data our stopping condition here
        max_run_time_secs = random.uniform(self.one_model_time, self.max_grid_runtime)
        max_run_time_secs = random.uniform(self.one_model_time, self.allowed_scaled_time*self.max_grid_runtime)
        search_criteria = {'strategy': 'RandomDiscrete', 'max_runtime_secs': max_run_time_secs,
                           "seed": int(round(time.time()))}
        # search_criteria = {'strategy': 'RandomDiscrete', 'max_runtime_secs': 1/1e8}

        print("GLM Binomial grid search_criteria: {0}".format(search_criteria))

        # fire off random grid-search
        grid_model = \
            H2OGridSearch(H2OGeneralizedLinearEstimator(family=self.family, nfolds=self.nfolds),
                          hyper_params=self.hyper_params, search_criteria=search_criteria)
        grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

        actual_run_time_secs = pyunit_utils.find_grid_runtime(grid_model)

        print("Maximum time limit is {0}.  Time taken to build all model is "
              "{1}".format(search_criteria["max_runtime_secs"], actual_run_time_secs))

        print("Maximum model number is {0}.  Actual number of models built is {1}".format(self.possible_number_models,
                                                                                          len(grid_model)))

        if actual_run_time_secs <= search_criteria["max_runtime_secs"]*(1+self.allowed_diff):
            print("test3_glm_random_grid_search_max_runtime_secs: passed!")

            if len(grid_model) > self.possible_number_models:   # generate too many models, something is wrong
                self.test_failed += 1
                self.test_failed_array[self.test_num] = 1
                print("test3_glm_random_grid_search_max_runtime_secs: failed.  Generated {0} models "
                      " which exceeds maximum possible model number {1}".format(len(grid_model),
                                                                                self.possible_number_models))
        elif len(grid_model) == 1:  # will always generate 1 model
            print("test3_glm_random_grid_search_max_runtime_secs: passed!")
        else:
            self.test_failed += 1
            self.test_failed_array[self.test_num] = 1
            print("test3_glm_random_grid_search_max_runtime_secs: failed.  Model takes time {0}"
                  " seconds which exceeds allowed time {1}".format(actual_run_time_secs,
                                                                   max_run_time_secs*(1+self.allowed_diff)))
        self.test_num += 1
        sys.stdout.flush()

    def test4_glm_random_grid_search_metric(self, metric_name, bigger_is_better):
        """
        This function will test the last stopping condition using metrics.

        :param metric_name: metric we want to use to test the last stopping condition
        :param bigger_is_better: higher metric value indicates better model performance

        :return: None
        """
        print("*******************************************************************************************")
        print("test4_glm_random_grid_search_metric using " + metric_name + " for family " + self.family)
        h2o.cluster_info()

        search_criteria = {
            "strategy": "RandomDiscrete",
            "stopping_metric": metric_name,
            "stopping_tolerance": random.uniform(1e-8, self.max_tolerance),
            "stopping_rounds": random.randint(1, self.max_stopping_rounds),
            "seed": int(round(time.time()))
        }

        print("GLM Binomial grid search_criteria: {0}".format(search_criteria))

        # add max_runtime_secs back into hyper-parameters to limit model runtime.
        self.hyper_params["max_runtime_secs"] = [0.3]   # arbitrarily set

        # fire off random grid-search
        grid_model = \
            H2OGridSearch(H2OGeneralizedLinearEstimator(family=self.family, nfolds=self.nfolds),
                          hyper_params=self.hyper_params, search_criteria=search_criteria)
        grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

        # bool indicating if randomized grid search has calculated the early stopping condition correctly
        stopped_correctly = \
            pyunit_utils.evaluate_metrics_stopping(grid_model.models, metric_name, bigger_is_better, search_criteria,
                                                   self.possible_number_models)

        if stopped_correctly:
            print("test4_glm_random_grid_search_metric " + metric_name + ": passed. ")
        else:
            self.test_failed += 1
            self.test_failed_array[self.test_num] = 1
            print("test4_glm_random_grid_search_metric " + metric_name + ": failed. ")

        self.test_num += 1


def test_random_grid_search_for_glm():
    """
    Create and instantiate classes, call test methods to test randomize grid search for GLM Gaussian
    or Binomial families.

    :return: None
    """

    # randomize grid search for Binomial
    test_glm_binomial_random_grid = Test_glm_random_grid_search("binomial")
    test_glm_binomial_random_grid.test1_glm_random_grid_search_model_number("logloss(xval=True)")
    test_glm_binomial_random_grid.test2_glm_random_grid_search_max_model()
    test_glm_binomial_random_grid.test3_glm_random_grid_search_max_runtime_secs()
    test_glm_binomial_random_grid.test4_glm_random_grid_search_metric("logloss", False)
    test_glm_binomial_random_grid.test4_glm_random_grid_search_metric("AUC", True)
    # test_glm_binomial_random_grid.tear_down()

    # exit with error if any tests have failed
    if test_glm_binomial_random_grid.test_failed > 0:
        sys.exit(1)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_random_grid_search_for_glm)
else:
    test_random_grid_search_for_glm()
