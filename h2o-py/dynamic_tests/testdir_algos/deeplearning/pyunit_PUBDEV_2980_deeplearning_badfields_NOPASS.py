from __future__ import print_function

import sys
import os
from builtins import range

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator


class Test_PUBDEV_2980_deeplearning:
    """
    PUBDEV-2980: deeplearning return the field model._model_json['output']['scoring_history'].cell_values
    with different lengths.  Normally, it is length 3.  Sometimes, it is length 2.


    This class is created to train a deeplearning model with different parameters settings and re-create two
    models with different field length for debugging purposes.
    """
    # parameters denoting filenames of interested that store training/validation/test data sets in csv format
    training1_filename = "smalldata/gridsearch/gaussian_training1_set.csv"

    test_name = "pyunit_PUBDEV_2980_deeplearning.py"     # name of this test

    # store information about training/test data sets
    x_indices = []              # store predictor indices in the data set
    y_index = 0                 # store response index in the data set

    training1_data = []         # store training data sets
    test_failed = 0             # count total number of tests that have failed

    def __init__(self):
        self.setup_data()

    def setup_data(self):
        """
        This function performs all initializations necessary:
        load the data sets and set the training set indices and response column index
        """
        # preload datasets
        self.training1_data = h2o.import_file(path=pyunit_utils.locate(self.training1_filename))

        # set data set indices for predictors and response
        self.y_index = self.training1_data.ncol-1
        self.x_indices = list(range(self.y_index))


    def test_deeplearning_fieldnames(self):
        """
        test_deeplearning_fieldnames performs the following:
        a. build a deeplearning model with good parameters in good_params_list and good_model_params;
        b. build a deeplearning model with bad parameters in bad_params_list and bad_model_params;
        c. look at the length of the field in _model_json['output']['model_summary'].cell_values
        d. print out the two fields.
        """

        print("*******************************************************************************************")
        h2o.cluster_info()
        good_params_list = {'epochs': 10.0, 'seed': 1464835583, 'nfolds': 5, 'hidden_dropout_ratios': -0.07120188,
                             'fold_assignment': 'AUTO', 'hidden': 6, 'distribution': 'gaussian'}
        good_model_params = {'max_runtime_secs': 108.65307012692}

        good_model = H2ODeepLearningEstimator(**good_params_list)
        good_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data, **good_model_params)

        bad_params_list = {'hidden': 6, 'epochs': -2.0, 'seed': 1464825861, 'fold_assignment': 'AUTO',
                           'hidden_dropout_ratios': -0.07120188, 'nfolds': 5, 'distribution': 'gaussian'}
        bad_model_params = {'max_runtime_secs': 98.58063693984}

        bad_model = H2ODeepLearningEstimator(**bad_params_list)
        bad_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data, **bad_model_params)

        good_model_len = len(good_model._model_json['output']['scoring_history'].cell_values)
        bad_model_len = len(bad_model._model_json['output']['scoring_history'].cell_values)

        print("good_model._model_json['output']['scoring_history'].cell_values length is {0}.  "
              "bad_model._model_json['output']['scoring_history'].cell_values length is "
              "{1}".format(good_model_len, bad_model_len))

        if not(good_model_len == bad_model_len):
            self.test_failed = 1
            print("They are not equal for some reason....")
        else:
            print("They are equal.  Good.")

        print("Good model cell values is:\n {0}\n  Bad model cell values is:\n "
              "{1}\n".format(good_model._model_json['output']['scoring_history'].cell_values,
                             bad_model._model_json['output']['scoring_history'].cell_values))


def test_PUBDEV_2980_for_deeplearning():
    """
    Create and instantiate class and perform tests specified for deeplearning

    :return: None
    """
    test_deeplearning_grid = Test_PUBDEV_2980_deeplearning()
    test_deeplearning_grid.test_deeplearning_fieldnames()

    sys.stdout.flush()

    if test_deeplearning_grid.test_failed:  # exit with error if any tests have failed
        sys.exit(1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_PUBDEV_2980_for_deeplearning)
else:
    test_PUBDEV_2980_for_deeplearning()
