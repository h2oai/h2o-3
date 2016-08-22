from __future__ import print_function

import sys
import os
from builtins import range
import time

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils
from h2o.estimators.kmeans import H2OKMeansEstimator
from h2o.grid.grid_search import H2OGridSearch


class Test_kmeans_grid_search:
    """
    PUBDEV-1843: Grid testing.  Subtask 5.

    This class is created to test the gridsearch for kmeans algo over differ validation datasets.  Only one test
    is performed here.

    Test Descriptions:
        a. choose a fixed set of parameters to use in the hyper-parameters for gridsearch.  However, must grid over
           the validation_frame parameter.
        b. Next, build H2O kmeans models using grid search and check out the performance metrics with the different
           validation datasets.
    """

    # parameters set by users, change with care
    seed = round(time.time())

    # parameters denoting filenames of interested
    training1_filenames = "smalldata/gridsearch/kmeans_8_centers_3_coords.csv"
    validation_filenames = ["smalldata/gridsearch/kmeans_8_centers_3_coords_valid1.csv",
                            "smalldata/gridsearch/kmeans_8_centers_3_coords_valid2.csv",
                            "smalldata/gridsearch/kmeans_8_centers_3_coords_valid3.csv"]

    # System parameters, do not change.  Dire consequences may follow if you do

    # store information about training/test data sets
    x_indices = []              # store predictor indices in the data set
    training1_data = []         # store training data sets
    test_failed = 0             # count total number of tests that have failed

    # give the user opportunity to pre-assign hyper parameters for fixed values
    hyper_params = dict()
    hyper_params['init'] = ["Random"]
    hyper_params["max_runtime_secs"] = [0]     # 10 seconds
    hyper_params["max_iterations"] = [50]
    hyper_params["k"] = [8]
    hyper_params["seed"] = [seed]     # added see to make test more repeatable

    def __init__(self):
        self.setup_data()

    def setup_data(self):
        """
        This function performs all initializations necessary:
        load the training/validation data sets and set the training set indices
        """
        self.training1_data = h2o.import_file(path=pyunit_utils.locate(self.training1_filenames))
        self.x_indices = list(range(self.training1_data.ncol))

        self.hyper_params["validation_frame"] = []

        for fname in self.validation_filenames:
            temp = h2o.import_file(path=pyunit_utils.locate(fname))
            self.hyper_params["validation_frame"].append(temp.frame_id)

    def test_kmeans_grid_search_over_validation_datasets(self):
        """
        test_kmeans_grid_search_over_validation_datasets performs the following:
        a. build H2O kmeans models using grid search.
        b. For each model built using grid search, print out the total_sum_squares errors.
        c. If an exception was thrown, mark the test as failed.
        """
        print("*******************************************************************************************")
        print("test_kmeans_grid_search_over_validation_datasets for kmeans ")
        h2o.cluster_info()

        print("Hyper-parameters used here is {0}".format(self.hyper_params))

        # try:
        #   start grid search
        grid_model = H2OGridSearch(H2OKMeansEstimator(), hyper_params=self.hyper_params)
        grid_model.train(x=self.x_indices, training_frame=self.training1_data)

        for each_model in grid_model:
            summary_list = each_model._model_json["output"]["validation_metrics"]
            if (summary_list is not None) and (summary_list._metric_json is not None):
                grid_model_metrics = summary_list._metric_json['totss']
                print("total sum of squares of a model is: {0}".format(grid_model_metrics))
            else:
                print('model._model_json["output"]["validation_metrics"] of a model is None for some reason....')
        # except:
        #     print("test_grid_search_for_kmeans_over_validation_frame failed: exception was thrown for no reason.")
        #     self.test_failed += 1


def test_grid_search_for_kmeans_over_validation_frame():
    """
    Create and instantiate class and perform tests specified for kmeans

    :return: None
    """
    test_kmeans_grid = Test_kmeans_grid_search()
    test_kmeans_grid.test_kmeans_grid_search_over_validation_datasets()

    sys.stdout.flush()

    if test_kmeans_grid.test_failed:  # exit with error if any tests have failed
        sys.exit(1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_grid_search_for_kmeans_over_validation_frame)
else:
    test_grid_search_for_kmeans_over_validation_frame()
