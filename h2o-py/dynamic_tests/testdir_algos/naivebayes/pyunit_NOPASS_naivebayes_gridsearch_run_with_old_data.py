from __future__ import print_function

import sys
import random
import os
import math
from builtins import range
import time
import json

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils
from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator
from h2o.grid.grid_search import H2OGridSearch

class Test_naivebayes_grid_search:
    """
    PUBDEV-1843: Grid testing.  Subtask 2.

    This class is created to test the gridsearch for naivebayes algo and make sure it runs.  Only one test is
    performed here.

    Test Descriptions:
    test_rf_grid_search_over_params performs the following:
        a. No hyper-parameters or dataset is generated, they are all loaded from data files.
        b. Next, build H2O naivebayes models using grid search.  Count and make sure models
           are only built for hyper-parameters set to legal values.  No model is built for bad hyper-parameters
           values.  We should instead get a warning/error message printed out.
        c. For each model built using grid search, we will extract the parameters used in building
           that model and manually build a H2O naivebayes model.  Logloss are calculated from a test set
           to compare the performance of grid search model and our manually built model.  If their metrics
           are close, declare test success.  Otherwise, declare test failure.
        d. we will check and make sure the models are built within the max_runtime_secs time limit that was set
           for it as well.  If max_runtime_secs was exceeded, declare test failure as well.

    Note that for hyper-parameters containing all legal parameter names and parameter value lists with legal
    and illegal values, grid-models should be built for all combinations of legal parameter values.  For
    illegal parameter values, a warning/error message should be printed out to warn the user but the
    program should not throw an exception.
    """

    # parameters set by users, change with care

    allowed_diff = 1e-2   # difference allow between grid search model and manually built model MSEs

    random.seed(round(time.time()))

    # System parameters, do not change.  Dire consequences may follow if you do
    current_dir = os.path.dirname(os.path.realpath(sys.argv[1]))    # directory of this test file

    train_row_count = 0         # training data row count, randomly generated later
    train_col_count = 0         # training data column count, randomly generated later

    extra_time_fraction = 0.1   # since timing is never perfect, give some extra time on top of maximum runtime limit

    model_run_time = 0.0        # time taken to run a vanilla naivebayes model.  Determined later.
    allowed_runtime_diff = 0.05     # run time difference between naivebayes manually built and gridsearch models
                                    # before we attempt to compare training metrics.


    # parameters denoting filenames with absolute paths
    training1_data_file = '/Users/wendycwong/Documents/QAWork/fewer_model_runGridSearch/set2/gridsearch_naivebayes' \
                          '_training1_1462211678_set.csv'

    family = 'multinomial'     # choose default family to be gaussian
    training_metric = 'logloss'    # metrics by which we evaluate model performance

    test_name = "pyunit_naivebayes_gridsearch_over_all_params_large.py"     # name of this test

    # store information about training/test data sets
    x_indices = []              # store predictor indices in the data set
    y_index = 0                 # store response index in the data set

    training1_data = []         # store training data sets
    test_failed = 0             # count total number of tests that have failed

    # give the user opportunity to pre-assign hyper parameters for fixed values
    hyper_params = dict()
    hyper_params["fold_assignment"] = ["AUTO", "Random", "Modulo"]
    hyper_params["compute_metrics"] = [True]

    # parameters to be excluded from hyper parameter list even though they may be gridable
    exclude_parameter_lists = ['validation_frame', 'response_column', 'fold_column', 'offset_column',
                               'min_sdev', 'eps_sdev', 'seed']

    params_zero_one = ['min_prob', 'eps_prob']
    params_more_than_zero = []
    params_more_than_one = []
    params_zero_positive = ['max_runtime_secs']       # >= 0

    final_hyper_params = dict()     # store the final hyper-parameters that we are going to use

    possible_number_models = 27      # possible number of models built based on hyper-parameter specification
    correct_model_number = 0        # count number of models built with bad hyper-parameter specification
    true_correct_model_number = 0   # count number of models built with good hyper-parameter specification
    nfolds = 5                      # enable cross validation to test fold_assignment

    def __init__(self):
        self.setup_data()
        self.setup_model()

    def setup_data(self):
        """
        This function performs all initializations necessary:
        1. load the data sets and set the training set indices and response column index
        """

        # preload data sets
        self.training1_data = h2o.import_file(pyunit_utils.locate(self.training1_data_file))

        # set data set indices for predictors and response
        self.y_index = self.training1_data.ncol-1
        self.x_indices = list(range(self.y_index))

        # set response to be categorical for classification tasks
        self.training1_data[self.y_index] = self.training1_data[self.y_index].round().asfactor()

    def setup_model(self):
        """
        This function load the final_hyper_params used in the gridsearch test next.

        :return: None
        """
        # read in the final_hyper_params for our gridsearch
        with open('/Users/wendycwong/Documents/QAWork/fewer_model_runGridSearch/set2/gridsearch_naivebayes_'
                  'hyper_parameter_1462211678.json') as params_file:
            self.final_hyper_params = json.load(params_file)

    def test_naivebayes_grid_search_over_params(self):
        """
        test_naivebayes_grid_search_over_params performs the following:
        a. build H2O naivebayes models using grid search.  Count and make sure models
           are only built for hyper-parameters set to legal values.  No model is built for bad hyper-parameters
           values.  We should instead get a warning/error message printed out.
        b. For each model built using grid search, we will extract the parameters used in building
           that model and manually build a H2O naivebayes model.  Logloss are calculated from a test set
           to compare the performance of grid search model and our manually built model.  If their metrics
           are close, declare test success.  Otherwise, declare test failure.
        c. we will check and make sure the models are built within the max_runtime_secs time limit that was set
           for it as well.  If max_runtime_secs was exceeded, declare test failure as well.
        """
        print("*******************************************************************************************")
        print("test_naivebayes_grid_search_over_params for naivebayes ")
        h2o.cluster_info()

        try:
            print("Hyper-parameters used here is {0}".format(self.final_hyper_params))

            # start grid search
            grid_model = H2OGridSearch(H2ONaiveBayesEstimator(nfolds=self.nfolds),
                                   hyper_params=self.final_hyper_params)
            grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

            self.correct_model_number = len(grid_model)     # store number of models built

            # make sure the correct number of models are built by gridsearch
            if not (self.correct_model_number == self.possible_number_models):  # wrong grid model number
                self.test_failed += 1
                print("test_naivebayes_grid_search_over_params for naivebayes failed: number of models built by "
                      "gridsearch does not equal to all possible combinations of hyper-parameters")
            else:
                # add parameters into params_dict.  Use this to manually build model
                params_dict = dict()
                params_dict["nfolds"] = self.nfolds
                params_dict["score_tree_interval"] = 0
                total_run_time_limits = 0.0   # calculate upper bound of max_runtime_secs
                true_run_time_limits = 0.0
                manual_run_runtime = 0.0

                # compare performance metric of model built by gridsearch with manually built model
                for each_model in grid_model:

                    params_list = grid_model.get_hyperparams_dict(each_model._id)
                    params_list.update(params_dict)

                    model_params = dict()

                    # need to taken out max_runtime_secs from model parameters, it is now set in .train()
                    if "max_runtime_secs" in params_list:
                        model_params["max_runtime_secs"] = params_list["max_runtime_secs"]
                        max_runtime = params_list["max_runtime_secs"]
                        del params_list["max_runtime_secs"]
                    else:
                        max_runtime = 0

                    if "validation_frame" in params_list:
                        model_params["validation_frame"] = params_list["validation_frame"]
                        del params_list["validation_frame"]

                    if "score_tree_interval" in params_list:
                        model_params["score_tree_interval"] = params_list["score_tree_interval"]
                        del params_list["score_tree_interval"]

                    if "eps_prob" in params_list:
                        model_params["eps_prob"] = params_list["eps_prob"]
                        del params_list["eps_prob"]

                    if "min_prob" in params_list:
                        model_params["min_prob"] = params_list["min_prob"]
                        del params_list["min_prob"]

                    # make sure manual model was provided the same max_runtime_secs as the grid model
                    each_model_runtime = pyunit_utils.find_grid_runtime([each_model])

                    manual_model = H2ONaiveBayesEstimator(**params_list)
                    manual_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data,
                                       **model_params)

                    # collect the time taken to manually built all models
                    model_runtime = pyunit_utils.find_grid_runtime([manual_model])  # time taken to build this model
                    manual_run_runtime += model_runtime

                    if max_runtime > 0:
                        # shortest possible time it takes to build this model
                        if (max_runtime < self.model_run_time):
                            total_run_time_limits += model_runtime
                        else:
                            total_run_time_limits += max_runtime

                    true_run_time_limits += max_runtime

                    # compute and compare test metrics between the two models
                    test_grid_model_metrics = \
                        each_model.model_performance(test_data=self.training1_data)._metric_json[self.training_metric]
                    test_manual_model_metrics = \
                        manual_model.model_performance(test_data=self.training1_data)._metric_json[self.training_metric]

                    # just compare the mse in this case within tolerance:
                    if (each_model_runtime > 0) and \
                            (abs(model_runtime - each_model_runtime)/each_model_runtime < self.allowed_runtime_diff) \
                            and (abs(test_grid_model_metrics - test_manual_model_metrics) > self.allowed_diff):
                        self.test_failed += 1             # count total number of tests that have failed
                        print("test_naivebayes_grid_search_over_params for naivebayes failed: grid search model and manually "
                              "built H2O model differ too much in test MSE!")
                        break

                total_run_time_limits = max(total_run_time_limits, true_run_time_limits) * (1+self.extra_time_fraction)

                # make sure the max_runtime_secs is working to restrict model built time
                if not(manual_run_runtime <= total_run_time_limits):
                    self.test_failed += 1
                    print("test_naivebayes_grid_search_over_params for naivebayes failed: time taken to manually build models is {0}."
                          "  Maximum allowed time is {1}".format(manual_run_runtime, total_run_time_limits))

                if self.test_failed == 0:
                    print("test_naivebayes_grid_search_over_params for naivebayes has passed!")
        except:
            if self.possible_number_models > 0:
                print("test_naivebayes_grid_search_over_params for naivebayes failed: exception was thrown for no reason.")
                self.test_failed += 1


def test_grid_search_for_naivebayes_over_all_params():
    """
    Create and instantiate class and perform tests specified for naivebayes

    :return: None
    """
    test_naivebayes_grid = Test_naivebayes_grid_search()
    test_naivebayes_grid.test_naivebayes_grid_search_over_params()

    sys.stdout.flush()

    if test_naivebayes_grid.test_failed:  # exit with error if any tests have failed
        sys.exit(1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_grid_search_for_naivebayes_over_all_params)
else:
    test_grid_search_for_naivebayes_over_all_params()
