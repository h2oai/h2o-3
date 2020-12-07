from __future__ import print_function

import sys
import random
import os
from builtins import range
import time

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.grid.grid_search import H2OGridSearch


class Test_deeplearning_grid_search:
    """
    PUBDEV-1843: Grid testing.  Subtask 2.

    This class is created to test the gridsearch for deeplearning algo and make sure it runs.  Only one test is
    performed here.

    Test Descriptions:
        a. grab all truely griddable parameters and randomly or manually set the parameter values.
        b. Next, build H2O deeplearning models using grid search.  No model is built for bad hyper-parameters
           values.  We should instead get a warning/error message printed out.  Due to the multitude of warning
           messages about not returning a model when gridsearch parameters are set to be unreasonable, we will
           not attempt to count the number of gridsearch models built here.  Since we test this feature for all
           other algos, we are skipping it here.
        c. For each model built using grid search, we will extract the parameters used in building
           that model and manually build a H2O deeplearning model.  Training metrics are calculated from the
           gridsearch model and the manually built model.  If their metrics
           differ by too much, print a warning message but don't fail the test.
        d. we will check and make sure the models are built within the max_runtime_secs time limit that was set
           for it as well.  If max_runtime_secs was exceeded, declare test failure.

    Note that for hyper-parameters containing all legal parameter names and parameter value lists with legal
    and illegal values, grid-models should be built for all combinations of legal parameter values.  For
    illegal parameter values, a warning/error message should be printed out to warn the user but the
    program should not throw an exception.

    We will re-use the dataset generation methods for GLM.
    """

    # parameters set by users, change with care
    max_grid_model = 100          # maximum number of grid models generated before adding max_runtime_secs

    curr_time = str(round(time.time()))     # store current timestamp, used as part of filenames.
    seed = int(round(time.time()))

    # parameters denoting filenames of interested that store training/validation/test data sets in csv format
    training1_filename = "smalldata/gridsearch/gaussian_training1_set.csv"
    json_filename = "gridsearch_deeplearning_hyper_parameter_" + curr_time + ".json"

    allowed_diff = 1e-2   # difference allow between grid search model and manually built model MSEs

    # System parameters, do not change.  Dire consequences may follow if you do
    current_dir = os.path.dirname(os.path.realpath(sys.argv[1]))    # directory of this test file

    train_row_count = 0         # training data row count, randomly generated later
    train_col_count = 0         # training data column count, randomly generated later

    # following parameters are used to generate hyper-parameters
    max_int_val = 10            # maximum size of random integer values
    min_int_val = -2           # minimum size of random integer values
    max_int_number = 3          # maximum number of integer random grid values to generate

    max_real_val = 1            # maximum size of random float values
    min_real_val = -0.1           # minimum size of random float values
    max_real_number = 3         # maximum number of real grid values to generate

    time_scale = 2              # maximum runtime scale
    extra_time_fraction = 0.5   # since timing is never perfect, give some extra time on top of maximum runtime limit
    min_runtime_per_iteration = 0    # minimum run time found.  Determined later
    model_run_time = 0.0        # time taken to run a vanilla deeplearning model.  Determined later.
    allowed_runtime_diff = 0.05     # run time difference between deeplearning manually built and gridsearch models
                                    # before we attempt to compare training metrics.

    family = 'gaussian'     # choose default family to be gaussian
    training_metric = 'MSE'    # metrics by which we evaluate model performance

    test_name = "pyunit_deeplearning_gridsearch_over_all_params_large.py"     # name of this test
    sandbox_dir = ""  # sandbox directory where we are going to save our failed test data sets

    # store information about training/test data sets
    x_indices = []              # store predictor indices in the data set
    y_index = 0                 # store response index in the data set

    training1_data = []         # store training data sets
    test_failed = 0             # count total number of tests that have failed

    # give the user opportunity to pre-assign hyper parameters for fixed values
    hyper_params = dict()
    # hyper_params["balance_classes"] = [True, False]    # for classification only
    hyper_params["fold_assignment"] = ["AUTO", "Random", "Modulo", "Stratified"]
    hyper_params["activation"] = ["Tanh", "TanhWithDropout"]
    hyper_params["quiet_mode"] = [True]
    hyper_params["initial_weight_distribution"] = ["Uniform", "UniformAdaptive", "Normal"]
    hyper_params['variable_importances'] = [False]
    hyper_params['fast_mode'] = [True, False]
    hyper_params['shuffle_training_data'] = [False]

    # parameters to be excluded from hyper parameter list even though they may be gridable
    exclude_parameter_lists = ['distribution', 'tweedie_power', 'validation_frame', 'response_column',
                               'overwrite_with_best_model', 'quantile_alpha', 'max_confusion_matrix_size',
                               'train_samples_per_iteration', 'replicate_training_data', 'sparse', 'initial_weights',
                               'class_sampling_factors', 'standardize', 'fold_column', 'weights_column',
                               'offset_column', 'score_each_iteration', 'seed', 'max_w2', 'initial_weight_scale',
                               'score_interval', 'score_training_samples', 'score_validation_samples', 'sparsity_beta',
                               'classification_stop', 'regression_stop',  'score_validation_sampling',
                               'force_load_balance', 'single_node_mode', 'col_major', 'average_activation',
                               'score_duty_cycle', 'max_after_balance_size', 'nesterov_accelerated_gradient',
                               'max_categorical_features', 'reproducible', 'missing_values_handling', 'initial_biases',
                               'stopping_rounds', 'stopping_tolerance', 'stopping_metric', 'target_ratio_comm_to_comp',
                               'stopping_metric', 'balance_classes']

    final_hyper_params = dict()     # store the final hyper-parameters that we are going to use
    gridable_parameters = []    # store griddable parameter names
    gridable_types = []         # store the corresponding griddable parameter types
    gridable_defaults = []      # store the gridabble parameter default values

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

        # preload datasets
        self.training1_data = h2o.import_file(path=pyunit_utils.locate(self.training1_filename))

        # set data set indices for predictors and response
        self.y_index = self.training1_data.ncol-1
        self.x_indices = list(range(self.y_index))

        # save the training data files just in case the code crashed.
        pyunit_utils.remove_csv_files(self.current_dir, ".csv", action='copy', new_dir_path=self.sandbox_dir)

    def setup_model(self):
        """
        This function setup the gridsearch hyper-parameters that will be used later on:

        1. It will first try to grab all the parameters that are griddable and parameters used by deeplearning.
        2. It will find the intersection of parameters that are both griddable and used by deeplearning.
        3. There are several extra parameters that are used by deeplearning that are denoted as griddable but actually
        is not.  These parameters have to be discovered manually and they These are captured in
        self.exclude_parameter_lists.
        4. We generate the gridsearch hyper-parameter.  For numerical parameters, we will generate those randomly.
        For enums, we will include all of them.

        :return: None
        """
        # build bare bone model to get all parameters
        model = H2ODeepLearningEstimator(distribution=self.family, seed=self.seed, nfolds=self.nfolds,
                                         hidden=[10, 10, 10])
        model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

        self.model_run_time = pyunit_utils.find_grid_runtime([model])  # find model train time
        print("Time taken to build a base barebone model is {0}".format(self.model_run_time))

        summary_list = model._model_json["output"]["scoring_history"]
        num_iterations = summary_list["iterations"][2]

        if num_iterations == 0:
            self.min_runtime_per_iteration = self.model_run_time
        else:
            self.min_runtime_per_iteration = self.model_run_time / num_iterations

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

        if "epsilon" in list(self.hyper_params):
            self.hyper_params["epsilon"] = [1e-4 * x for x in self.hyper_params["epsilon"]]

        if "input_dropout_ratio" in list(self.hyper_params):
            self.hyper_params["input_dropout_ratio"] = [0.5 * x for x in self.hyper_params["input_dropout_ratio"]]

        if "hidden_dropout_ratio" in list(self.hyper_params):
            self.hyper_params["hidden_dropout_ratio"] = [0.5 * x for x in self.hyper_params["hidden_dropout_ratio"]]

        if "hidden" in list(self.hyper_params):     # need to change this up
            # randomly generate the number of layers in the network
            num_layer = random.randint(1,3)

            # for each layer, randomly generate the number of nodes in it
            self.hyper_params["hidden"] = [random.randint(1, self.max_int_val) for p in range(0, num_layer)]

        if "epochs" in self.hyper_params:
            self.hyper_params["epochs"] = [random.randint(self.min_int_val, self.max_int_val) for p in
                                           range(0, self.max_int_number)]

        # generate a new final_hyper_params which only takes a subset of all griddable parameters
        [self.possible_number_models, self.final_hyper_params] = \
            pyunit_utils.check_and_count_models(self.hyper_params, [], [],
                                                [], [],
                                                self.max_grid_model)

        # must add max_runtime_secs to restrict unit test run time and as a promise to Arno to test for this
        if ("max_runtime_secs" not in list(self.final_hyper_params)) and \
                ("max_runtime_secs" in list(self.hyper_params)):
            self.final_hyper_params["max_runtime_secs"] = self.hyper_params["max_runtime_secs"]

        self.final_hyper_params["seed"] = [self.seed]     # added see to make test more repeatable

        # write out the hyper-parameters used into json files.
        pyunit_utils.write_hyper_parameters_json(self.current_dir, self.sandbox_dir, self.json_filename,
                                                 self.final_hyper_params)

    def test_deeplearning_grid_search_over_params(self):
        """
        test_deeplearning_fieldnames performs the following:
        a. build H2O deeplearning models using grid search.  Count and make sure models
           are only built for hyper-parameters set to legal values.  No model is built for bad hyper-parameters
           values.  We should instead get a warning/error message printed out.
        c. For each model built using grid search, we will extract the parameters used in building
           that model and manually build a H2O deeplearning model.  Training metrics are calculated from the
           gridsearch model and the manually built model.  If their metrics
           differ by too much, print a warning message but don't fail the test.
        d. we will check and make sure the models are built within the max_runtime_secs time limit that was set
           for it as well.  If max_runtime_secs was exceeded, declare test failure.
        """

        print("*******************************************************************************************")
        print("test_deeplearning_fieldnames for deeplearning " + self.family)
        h2o.cluster_info()

        # start grid search
        # grid_model = H2OGridSearch(H2ODeepLearningEstimator(nfolds=self.nfolds, seed=self.seed),
        #                             hyper_params=self.final_hyper_params)
        # grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)
        #
        # self.correct_model_number = len(grid_model)     # store number of models built

        try:
            print("Hyper-parameters used here is {0}".format(self.final_hyper_params))

            # start grid search
            grid_model = H2OGridSearch(H2ODeepLearningEstimator(nfolds=self.nfolds),
                                       hyper_params=self.final_hyper_params)
            grid_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data)

            # add parameters into params_dict.  Use this to manually build model
            params_dict = dict()
            params_dict["distribution"] = self.family
            params_dict["nfolds"] = self.nfolds
            total_run_time_limits = 0.0   # calculate upper bound of max_runtime_secs
            true_run_time_limits = 0.0
            manual_run_runtime = 0.0

            # compare MSE performance of model built by gridsearch with manually built model
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

                if "elastic_averaging_moving_rate" in params_list:
                    model_params["elastic_averaging_moving_rate"] = params_list["elastic_averaging_moving_rate"]
                    del params_list["elastic_averaging_moving_rate"]

                if "validation_frame" in params_list:
                    model_params["validation_frame"] = params_list["validation_frame"]
                    del params_list["validation_frame"]

                if "elastic_averaging_regularization" in params_list:
                    model_params["elastic_averaging_regularization"] = params_list["elastic_averaging_regularization"]
                    del params_list["elastic_averaging_regularization"]

                if "hidden" in params_list:
                    temp = params_list["hidden"]
                    params_list["hidden"] = [temp]

                if "hidden_dropout_ratios" in params_list:
                    temp = params_list["hidden_dropout_ratios"]
                    params_list["hidden_dropout_ratios"] = [temp]

                manual_model = H2ODeepLearningEstimator(**params_list)
                manual_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training1_data,
                                   **model_params)

                # collect the time taken to manually built all models
                model_runtime = pyunit_utils.find_grid_runtime([manual_model])  # time taken to build this model
                manual_run_runtime += model_runtime

                summary_list = manual_model._model_json["output"]["scoring_history"]
                if len(summary_list.cell_values) < 3:
                    num_iterations = 1
                else:
                    num_iterations = summary_list["iterations"][2]

                if max_runtime > 0:
                    # shortest possible time it takes to build this model
                    if (max_runtime < self.min_runtime_per_iteration) or (num_iterations <= 1):
                        total_run_time_limits += model_runtime
                    else:
                        total_run_time_limits += max_runtime

                true_run_time_limits += max_runtime

                # compute and compare test metrics between the two models
                grid_model_metrics = each_model.model_performance()._metric_json[self.training_metric]
                manual_model_metrics = manual_model.model_performance()._metric_json[self.training_metric]

                # just compare the mse in this case within tolerance:
                if not((type(grid_model_metrics) == str) or (type(manual_model_metrics) == str)):
                    if (abs(grid_model_metrics) > 0) \
                            and abs(grid_model_metrics - manual_model_metrics)/grid_model_metrics > self.allowed_diff:
                        print("test_deeplearning_fieldnames for deeplearning warning: grid search "
                              "model metric ({0}) and manually built H2O model metric ({1}) differ too much"
                              "!".format(grid_model_metrics, manual_model_metrics))

            total_run_time_limits = max(total_run_time_limits, true_run_time_limits) * (1+self.extra_time_fraction)

            # make sure the max_runtime_secs is working to restrict model built time
            if not(manual_run_runtime <= total_run_time_limits):
                self.test_failed += 1
                print("test_deeplearning_fieldnames for deeplearning failed: time taken to manually build"
                      " models is {0}.  Maximum allowed time is {1}".format(manual_run_runtime, total_run_time_limits))
            else:
                print("time taken to manually build all models is {0}. Maximum allowed time is "
                      "{1}".format(manual_run_runtime, total_run_time_limits))

            if self.test_failed == 0:
                print("test_deeplearning_fieldnames for deeplearning has passed!")
        except Exception as e:
            if len(grid_model) > 0:
                print("test_deeplearning_fieldnames for deeplearning failed: exception ({0}) was thrown for "
                      "no reason.".format(e))
                self.test_failed += 1


def test_grid_search_for_deeplearning_over_all_params():
    """
    Create and instantiate class and perform tests specified for deeplearning

    :return: None
    """
    test_deeplearning_grid = Test_deeplearning_grid_search()
    test_deeplearning_grid.test_deeplearning_grid_search_over_params()

    sys.stdout.flush()

    if test_deeplearning_grid.test_failed:  # exit with error if any tests have failed
        sys.exit(1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_grid_search_for_deeplearning_over_all_params)
else:
    test_grid_search_for_deeplearning_over_all_params()
