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
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.grid.grid_search import H2OGridSearch

class Test_rf_gridsearch_sorting_metrics:
    """
    PUBDEV-2967: gridsearch sorting metric with cross-validation.

    This class is created to test that when cross-validation is enabled, the gridsearch models are returned sorted
    according to the cross-validation metrics.

    Test Descriptions:
        a. grab all truely griddable parameters and randomly or manually set the parameter values.
        b. Next, build H2O random forest models using grid search.  No model is built for bad hyper-parameters
           values.  We should instead get a warning/error message printed out.
        c. Check and make sure that the models are returned sorted with the correct cross-validation metrics.

    Note that for hyper-parameters containing all legal parameter names and parameter value lists with legal
    and illegal values, grid-models should be built for all combinations of legal parameter values.  For
    illegal parameter values, a warning/error message should be printed out to warn the user but the
    program should not throw an exception;

    We will re-use the dataset generation methods for GLM.  There will be only one data set for classification.
    """

    # parameters set by users, change with care
    max_grid_model = 25           # maximum number of grid models generated before adding max_runtime_secs

    diff = 1e-10            # comparison threshold

    curr_time = str(round(time.time()))     # store current timestamp, used as part of filenames.
    seed = int(round(time.time()))

    # parameters denoting filenames of interested that store training/validation/test data sets in csv format
    training1_filename = "smalldata/gridsearch/multinomial_training1_set.csv"
    json_filename = "gridsearch_rf_hyper_parameter_" + curr_time + ".json"

    # System parameters, do not change.  Dire consequences may follow if you do
    current_dir = os.path.dirname(os.path.realpath(sys.argv[1]))    # directory of this test file

    train_row_count = 0         # training data row count, randomly generated later
    train_col_count = 0         # training data column count, randomly generated later

    # following parameters are used to generate hyper-parameters
    max_int_val = 10            # maximum size of random integer values
    min_int_val = 0           # minimum size of random integer values
    max_int_number = 2          # maximum number of integer random grid values to generate

    max_real_val = 1            # maximum size of random float values
    min_real_val = 0           # minimum size of random float values
    max_real_number = 2         # maximum number of real grid values to generate

    time_scale = 2              # maximum runtime scale

    family = 'multinomial'     # choose default family to be gaussian
    training_metric = 'logloss'    # metrics by which we evaluate model performance

    test_name = "pyunit_rf_gridsearch_sorting_metrics.py"     # name of this test
    sandbox_dir = ""  # sandbox directory where we are going to save our failed test data sets

    # store information about training/test data sets
    x_indices = []              # store predictor indices in the data set
    y_index = 0                 # store response index in the data set

    training1_data = []         # store training data sets
    test_failed = 0             # count total number of tests that have failed

    # give the user opportunity to pre-assign hyper parameters for fixed values
    hyper_params = dict()
    hyper_params["balance_classes"] = [True, False]
    hyper_params["fold_assignment"] = ["AUTO", "Random", "Modulo", "Stratified"]
    hyper_params["stopping_metric"] = ['logloss']

    # parameters to be excluded from hyper parameter list even though they may be gridable
    exclude_parameter_lists = ['validation_frame', 'response_column', 'fold_column', 'offset_column',
                               'col_sample_rate_change_per_level', 'sample_rate_per_class', 'col_sample_rate_per_tree',
                               'nbins', 'nbins_top_level', 'nbins_cats', 'seed', 'class_sampling_factors',
                               'max_after_balance_size', 'min_split_improvement', 'histogram_type', 'mtries',
                               'weights_column', 'min_rows', 'r2_stopping', 'score_tree_interval']

    params_zero_one = ["sample_rate"]
    params_more_than_zero = ['ntrees', 'max_depth']
    params_more_than_one = []
    params_zero_positive = ['max_runtime_secs', 'stopping_rounds', 'stopping_tolerance']       # >= 0

    final_hyper_params = dict()     # store the final hyper-parameters that we are going to use
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
        load the data sets and set the training set indices and response column index
        """

        # create and clean out the sandbox directory first
        self.sandbox_dir = pyunit_utils.make_Rsandbox_dir(self.current_dir, self.test_name, True)

        # preload data sets
        self.training1_data = h2o.import_file(path=pyunit_utils.locate(self.training1_filename))

        # set data set indices for predictors and response
        self.y_index = self.training1_data.ncol-1
        self.x_indices = list(range(self.y_index))

        # set response to be categorical for classification tasks
        self.training1_data[self.y_index] = self.training1_data[self.y_index].round().asfactor()

        # save the training data files just in case the code crashed.
        pyunit_utils.remove_csv_files(self.current_dir, ".csv", action='copy', new_dir_path=self.sandbox_dir)

    def setup_model(self):
        """
        This function setup the gridsearch hyper-parameters that will be used later on:

        1. It will first try to grab all the parameters that are griddable and parameters used by random forest.
        2. It will find the intersection of parameters that are both griddable and used by random forest.
        3. There are several extra parameters that are used by random forest that are denoted as griddable but actually
        are not.  These parameters have to be discovered manually and they are captured in
        self.exclude_parameter_lists.
        4. We generate the gridsearch hyper-parameter.  For numerical parameters, we will generate those randomly.
        For enums, we will include all of them.

        :return: None
        """
        # build bare bone model to get all parameters
        model = H2ORandomForestEstimator(ntrees=self.max_int_val, nfolds=self.nfolds, score_tree_interval=0)
        model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

        self.model_run_time = pyunit_utils.find_grid_runtime([model])  # find model train time
        print("Time taken to build a base barebone model is {0}".format(self.model_run_time))

        summary_list = model._model_json["output"]["model_summary"]
        num_trees = summary_list["number_of_trees"][0]

        if num_trees == 0:
            self.min_runtime_per_tree = self.model_run_time
        else:
            self.min_runtime_per_tree = self.model_run_time/num_trees

        # grab all gridable parameters and its type
        (self.gridable_parameters, self.gridable_types, self.gridable_defaults) = \
            pyunit_utils.get_gridables(model._model_json["parameters"])

        # randomly generate griddable parameters including values outside legal range, like setting alpha values to
        # be outside legal range of 0 and 1 and etc
        (self.hyper_params, self.gridable_parameters, self.gridable_types, self.gridable_defaults) = \
            pyunit_utils.gen_grid_search(model.full_parameters.keys(), self.hyper_params,
                                         self.exclude_parameter_lists,
                                         self.gridable_parameters, self.gridable_types, self.gridable_defaults,
                                         random.randint(1, self.max_int_number),
                                         self.max_int_val, self.min_int_val,
                                         random.randint(1, self.max_real_number),
                                         self.max_real_val, self.min_real_val)

        # scale the max_runtime_secs parameter and others as well to make sure they make sense
        time_scale = self.time_scale * self.model_run_time
        if "max_runtime_secs" in list(self.hyper_params):
            self.hyper_params["max_runtime_secs"] = [time_scale * x for x
                                                     in self.hyper_params["max_runtime_secs"]]

        # generate a new final_hyper_params which only takes a subset of all griddable parameters while
        # hyper_params take all griddable parameters and generate the grid search hyper-parameters
        [self.possible_number_models, self.final_hyper_params] = \
            pyunit_utils.check_and_count_models(self.hyper_params, self.params_zero_one, self.params_more_than_zero,
                                                self.params_more_than_one, self.params_zero_positive,
                                                self.max_grid_model)

        # must add max_runtime_secs to restrict unit test run time and as a promise to Arno to test for this
        if ("max_runtime_secs" not in list(self.final_hyper_params)) and \
                ("max_runtime_secs" in list(self.hyper_params)):
            self.final_hyper_params["max_runtime_secs"] = self.hyper_params["max_runtime_secs"]
            len_good_time = len([x for x in self.hyper_params["max_runtime_secs"] if (x >= 0)])
            self.possible_number_models = self.possible_number_models*len_good_time

        # write out the hyper-parameters used into json files.
        pyunit_utils.write_hyper_parameters_json(self.current_dir, self.sandbox_dir, self.json_filename,
                                                 self.final_hyper_params)

    def test_rf_gridsearch_sorting_metrics(self):
        """
        test_rf_gridsearch_sorting_metrics performs the following:
        b. build H2O random forest models using grid search.  No model is built for bad hyper-parameters
           values.  We should instead get a warning/error message printed out.
        c. Check and make sure that the models are returned sorted with the correct cross-validation metrics.
        """

        if self.possible_number_models > 0:
            print("*******************************************************************************************")
            print("test_rf_gridsearch_sorting_metrics for random forest ")
            h2o.cluster_info()


            print("Hyper-parameters used here is {0}".format(self.final_hyper_params))

            # start grid search
            grid_model = H2OGridSearch(H2ORandomForestEstimator(nfolds=self.nfolds, seed=self.seed,
                                                                score_tree_interval=0),
                                       hyper_params=self.final_hyper_params)
            grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

            result_table = grid_model._grid_json["summary_table"]
            model_index = 0
            grid_model_metrics = []

            diff = 0    # calculate difference between gridsearch model metrics and manually extracted model.
            diff_train = 0  # calculate difference between training and cross-validation metrics

            # grab performance metric for each model of grid_model and collect correct sorting metrics by hand
            for each_model in grid_model:
                grid_model_metric = float(result_table[self.training_metric][model_index])
                grid_model_metrics.append(grid_model_metric)

                manual_metric = each_model._model_json["output"]["cross_validation_metrics"]._metric_json["logloss"]
                if not(type(grid_model_metrics) == unicode) and not(type(manual_metric)==unicode):
                    diff += abs(grid_model_metric - manual_metric)

                manual_training_metric = each_model._model_json["output"]["training_metrics"]._metric_json["logloss"]
                if not(type(grid_model_metrics) == unicode) and not(type(manual_training_metric)==unicode):
                    diff_train += abs(grid_model_metric-manual_training_metric)

                print("grid model logloss: {0}, grid model training logloss: "
                      "{1}".format(grid_model_metric, manual_training_metric))

                model_index += 1

            if (diff > self.diff) or not(grid_model_metrics == sorted(grid_model_metrics)) or (diff_train < self.diff):
                self.test_failed = 1
                print("test_rf_gridsearch_sorting_metrics for random forest has failed!")

            if self.test_failed == 0:
                print("test_rf_gridsearch_sorting_metrics for random forest has passed!")



def test_gridsearch_sorting_metrics():
    """
    Create and instantiate class and perform tests specified for random forest

    :return: None
    """
    test_rf_grid = Test_rf_gridsearch_sorting_metrics()
    test_rf_grid.test_rf_gridsearch_sorting_metrics()

    sys.stdout.flush()

    if test_rf_grid.test_failed:  # exit with error if any tests have failed
        sys.exit(1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gridsearch_sorting_metrics)
else:
    test_gridsearch_sorting_metrics()
