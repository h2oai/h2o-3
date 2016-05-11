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

    This class is created to debug the problems we have with gridsearch.  In particular, we have encountered
    several kinds of warning/error messages.  In this particular test, we look at a particular hyper-parameter
    value setting:

    Hyper-parameters used here is
    {'fold_assignment': 'AUTO', 'laplace': 8.3532975, 'max_runtime_secs': 0.009854314056000001}

    """
    # parameters set by users, change with care
    random.seed(round(time.time()))

    # System parameters, do not change.  Dire consequences may follow if you do
    current_dir = os.path.dirname(os.path.realpath(sys.argv[1]))    # directory of this test file

    train_row_count = 0         # training data row count, randomly generated later
    train_col_count = 0         # training data column count, randomly generated later

    extra_time_fraction = 0.1   # since timing is never perfect, give some extra time on top of maximum runtime limit

    model_run_time = 0.0        # time taken to run a vanilla naivebayes model.  Determined later.
    allowed_diff = 1e-2   # difference allow between grid search model and manually built model metrics
    allowed_runtime_diff = 0.05     # run time difference between naivebayes manually built and gridsearch models
                                    # before we attempt to compare training metrics.

    # parameters denoting filenames with absolute paths
    training1_data_file = '/Users/wendycwong/Documents/QAWork/fewer_model_runGridSearch/set2/gridsearch_naivebayes' \
                          '_training1_1462211678_set.csv'

    training_metric = 'logloss'    # metrics by which we evaluate model performance

    test_name = "pyunit_naivebayes_gridsearch_over_all_params_large.py"     # name of this test

    # store information about training/test data sets
    x_indices = []              # store predictor indices in the data set
    y_index = 0                 # store response index in the data set

    training1_data = []         # store training data sets
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
        run gridsearch model and then build each model manually and see if we receive the same error messages.
        """
        print("*******************************************************************************************")
        print("test_naivebayes_grid_search_over_params for naivebayes ")
        h2o.cluster_info()

        print("Hyper-parameters used here is {0}".format(self.final_hyper_params))

        # # start grid search
        # grid_model = H2OGridSearch(H2ONaiveBayesEstimator(nfolds=self.nfolds),
        #                            hyper_params=self.final_hyper_params)
        # grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

        # add parameters into params_dict.  Use this to manually build model, one at a time
        params_dict = dict()
        params_dict["nfolds"] = self.nfolds
        params_list = dict()
        params_list["fold_assignment"] = self.final_hyper_params["fold_assignment"][0]
 #       params_list["max_runtime_secs"] = self.final_hyper_params["max_runtime_secs"][1]
        params_list["max_runtime_secs"] = 10        # this will return full NB model

        # the field manual_model._model_json['output']['cross_validation_metrics_summary'].cell_values will be empty
        params_list["max_runtime_secs"] = 0.001     # this will not return full NB model
        params_list["laplace"] = self.final_hyper_params["laplace"][0]

        print("Hyper-parameters used here is {0}\n".format(params_list))

        params_list.update(params_dict)

        model_params = dict()

        # need to taken out max_runtime_secs from model parameters, it is now set in .train()
        if "max_runtime_secs" in params_list:
            model_params["max_runtime_secs"] = params_list["max_runtime_secs"]
            max_runtime = params_list["max_runtime_secs"]
            del params_list["max_runtime_secs"]
        else:
            max_runtime = 0

        manual_model = H2ONaiveBayesEstimator(**params_list)
        manual_model.train(x=self.x_indices, y=self.y_index,
                                        training_frame=self.training1_data, **model_params)

        print("Done!")


def test_grid_search_for_naivebayes_over_all_params():
    """
    Create and instantiate class and perform tests specified for naivebayes

    :return: None
    """
    test_naivebayes_grid = Test_naivebayes_grid_search()
    test_naivebayes_grid.test_naivebayes_grid_search_over_params()

    sys.stdout.flush()

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_grid_search_for_naivebayes_over_all_params)
else:
    test_grid_search_for_naivebayes_over_all_params()
