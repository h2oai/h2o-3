import sys
import random
import os
from builtins import range
import time

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils
from h2o.estimators.pca import H2OPrincipalComponentAnalysisEstimator as H2OPCA
from h2o.grid.grid_search import H2OGridSearch


class Test_PCA_grid_search:
    """
    PUBDEV-1843: Grid testing.  Subtask 2.

    This class is created to test the gridsearch for PCA algo and make sure it runs.  Only one test is performed
    here.

    Test Descriptions:
        a. grab all truely griddable parameters and randomly or manually set the parameter values.
        b. Next, build H2O PCA models using grid search.  Count and make sure models
           are only built for hyper-parameters set to legal values.  No model is built for bad hyper-parameters
           values.  We should instead get a warning/error message printed out.
        c. For each model built using grid search, we will extract the parameters used in building
           that model and manually build a H2O PCA model.  Training metrics are calculated from the
           gridsearch model and the manually built model.  If their metrics
           differ by too much, print a warning message but don't fail the test.
        d. we will check and make sure the models are built within the max_runtime_secs time limit that was set
           for it as well.  If max_runtime_secs was exceeded, declare test failure.

    Note that for hyper-parameters containing all legal parameter names and parameter value lists with legal
    and illegal values, grid-models should be built for all combinations of legal parameter values.  For
    illegal parameter values, a warning/error message should be printed out to warn the user but the
    program should not throw an exception.
    """

    # parameters set by users, change with care
    max_grid_model = 100           # maximum number of grid models generated before adding max_runtime_secs

    curr_time = str(round(time.time()))     # store current timestamp, used as part of filenames.
    seed = int(round(time.time()))

    # parameters denoting filenames of interested that store training/validation/test data sets in csv format
    training1_filenames = "smalldata/gridsearch/pca1000by25.csv"
    json_filename = "gridsearch_PCA_hyper_parameter_" + curr_time + ".json"

    allowed_diff = 1e-2   # difference allow between grid search model and manually built model MSEs

    # System parameters, do not change.  Dire consequences may follow if you do
    current_dir = os.path.dirname(os.path.realpath(sys.argv[0]))    # directory of this test file

    # following parameters are used to generate hyper-parameters
    max_int_val = 10            # maximum size of random integer values
    min_int_val = -2           # minimum size of random integer values
    max_int_number = 3          # maximum number of integer random grid values to generate

    max_real_val = 1            # maximum size of random float values
    min_real_val = -0.1           # minimum size of random float values
    max_real_number = 3         # maximum number of real grid values to generate

    time_scale = 2              # maximum runtime scale
    max_iter_scale = 10         # scale the maximum number of iterations to be 100 maximum
    extra_time_fraction = 0.5   # since timing is never perfect, give some extra time on top of maximum runtime limit
    model_run_time = 0.0        # time taken to run a vanilla PCA model.  Determined later.
    allowed_runtime_diff = 0.05     # run time difference between PCA manually built and gridsearch models before
                                    # we attempt to compare training metrics.

    test_name = "pyunit_PCA_gridsearch_over_all_params_large.py"     # name of this test
    sandbox_dir = ""  # sandbox directory where we are going to save our failed test data sets

    # store information about training/test data sets
    x_indices = []              # store predictor indices in the data set

    training1_data = []         # store training data sets
    test_failed = 0             # count total number of tests that have failed

    # give the user opportunity to pre-assign hyper parameters for fixed values
    hyper_params = dict()
    # hyper_params["pca_method"] = ["GramSVD", "Power"]
    hyper_params["transform"] = ["NONE","DEMEAN","DESCALE","STANDARDIZE","NORMALIZE"]
    pca_method = "GramSVD"

    # parameters to be excluded from hyper parameter list even though they may be gridable
    exclude_parameter_lists = ['model_id', 'seed', 'use_all_factor_levels', 'validation_frame', 'pca_method']

    params_zero_one = ['col_sample_rate', 'learn_rate_annealing', 'learn_rate', 'col_sample_rate_per_tree',
                       'sample_rate']
    params_more_than_zero = ['k', 'max_iterations']
    params_more_than_one = ['nbins_cats', 'nbins']
    params_zero_positive = ['max_runtime_secs', 'stopping_rounds', 'stopping_tolerance']       # >= 0

    final_hyper_params = dict()     # store the final hyper-parameters that we are going to use
    gridable_parameters = []    # store griddable parameter names
    gridable_types = []         # store the corresponding griddable parameter types
    gridable_defaults = []      # store the gridabble parameter default values

    possible_number_models = 0      # possible number of models built based on hyper-parameter specification
    correct_model_number = 0        # count number of models built with bad hyper-parameter specification
    true_correct_model_number = 0   # count number of models built with good hyper-parameter specification

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

        self.training1_data = h2o.import_file(path=pyunit_utils.locate(self.training1_filenames))
        self.x_indices = list(range(self.training1_data.ncol))

        # save the training data files just in case the code crashed.
        pyunit_utils.remove_csv_files(self.current_dir, ".csv", action='copy', new_dir_path=self.sandbox_dir)

    def setup_model(self):
        """
        This function setup the gridsearch hyper-parameters that will be used later on:

        1. It will first try to grab all the parameters that are griddable and parameters used by PCA.
        2. It will find the intersection of parameters that are both griddable and used by PCA.
        3. There are several extra parameters that are used by PCA that are denoted as griddable but actually is not.
        These parameters have to be discovered manually and they These are captured in self.exclude_parameter_lists.
        4. We generate the gridsearch hyper-parameter.  For numerical parameters, we will generate those randomly.
        For enums, we will include all of them.

        :return: None
        """
        # build bare bone model to get all parameters
        model = H2OPCA(k=10, transform="NONE", pca_method=self.pca_method)
        model.train(x=self.x_indices, training_frame=self.training1_data)

        self.model_run_time = pyunit_utils.find_grid_runtime([model])  # find model train time
        print("Time taken to build a base barebone model is {0}".format(self.model_run_time))

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

        # scale the max_runtime_secs parameters
        time_scale = self.time_scale * self.model_run_time
        if "max_runtime_secs" in list(self.hyper_params):
            self.hyper_params["max_runtime_secs"] = [time_scale * x for x
                                                     in self.hyper_params["max_runtime_secs"]]

        if 'max_iterations' in list(self.hyper_params):
            self.hyper_params['max_iterations'] = [self.max_iter_scale * x for x in self.hyper_params['max_iterations']]

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

        # must include k in hyper-parameters
        if ('k' not in list(self.final_hyper_params)) and ('k' in list(self.hyper_params)):
            self.final_hyper_params["k"] = self.hyper_params["k"]
            len_good_k = len([x for x in self.hyper_params["k"] if (x > 0)])
            self.possible_number_models = self.possible_number_models*len_good_k

        # write out the hyper-parameters used into json files.
        pyunit_utils.write_hyper_parameters_json(self.current_dir, self.sandbox_dir, self.json_filename,
                                                 self.final_hyper_params)

    def test_PCA_grid_search_over_params(self):
        """
        test_pca_grid_search_over_params: test for condition 1 and performs the following:
        a. build H2O PCA models using grid search.  Count and make sure models
           are only built for hyper-parameters set to legal values.  No model is built for bad hyper-parameters
           values.  We should instead get a warning/error message printed out.
        c. For each model built using grid search, we will extract the parameters used in building
           that model and manually build a H2O PCA model.  Training metrics are calculated from the
           gridsearch model and the manually built model.  If their metrics
           differ by too much, print a warning message but don't fail the test.
        d. we will check and make sure the models are built within the max_runtime_secs time limit that was set
           for it as well.  If max_runtime_secs was exceeded, declare test failure.
        """
        print("*******************************************************************************************")
        print("test_PCA_grid_search_over_params for PCA ")
        h2o.cluster_info()

        try:
            print("Hyper-parameters used here is {0}".format(self.final_hyper_params))

            # start grid search
            grid_model = H2OGridSearch(H2OPCA(pca_method=self.pca_method),
                                       hyper_params=self.final_hyper_params)
            grid_model.train(x=self.x_indices, training_frame=self.training1_data)

            self.correct_model_number = len(grid_model)     # store number of models built

            # make sure the correct number of models are built by gridsearch
            if (self.correct_model_number - self.possible_number_models)>0.9:  # wrong grid model number
                self.test_failed += 1
                print("test_PCA_grid_search_over_params for PCA failed: number of models built by gridsearch: {0} "
                      "does not equal to all possible combinations of hyper-parameters: "
                      "{1}".format(self.correct_model_number, self.possible_number_models))
            else:
                # add parameters into params_dict.  Use this to manually build model
                params_dict = dict()
                params_dict["pca_method"] = self.pca_method
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

                    # make sure manual model was provided the same max_runtime_secs as the grid model
                    each_model_runtime = pyunit_utils.find_grid_runtime([each_model])

                    manual_model = H2OPCA(**params_list)
                    manual_model.train(x=self.x_indices, training_frame=self.training1_data,
                                       **model_params)

                    # collect the time taken to manually built all models
                    model_runtime = pyunit_utils.find_grid_runtime([manual_model])  # time taken to build this model
                    manual_run_runtime += model_runtime

                    if max_runtime > 0:
                        # shortest possible time it takes to build this model
                        if max_runtime < self.model_run_time:
                            total_run_time_limits += model_runtime
                        else:
                            total_run_time_limits += max_runtime

                    true_run_time_limits += max_runtime

                    # compute and compare test metrics between the two models
                    grid_model_metrics = \
                        sum(each_model._model_json["output"]["model_summary"].cell_values[0][1:params_list["k"]])
                    manual_model_metrics = \
                        sum(manual_model._model_json["output"]["model_summary"].cell_values[0][1:params_list["k"]])

                    # just compare the mse in this case within tolerance:
                    if not((type(grid_model_metrics) == str) or (type(manual_model_metrics) == str)):
                        if (abs(grid_model_metrics) > 0) and \
                                (abs(grid_model_metrics - manual_model_metrics)/grid_model_metrics > self.allowed_diff):
                            print("test_PCA_grid_search_over_params for PCA warning: grid search model mdetric ({0}) "
                                  "and manually built H2O model metric ({1}) differ too much"
                                  "!".format(grid_model_metrics, manual_model_metrics))

                total_run_time_limits = max(total_run_time_limits, true_run_time_limits) * (1+self.extra_time_fraction)

                # make sure the max_runtime_secs is working to restrict model built time
                if not(manual_run_runtime <= total_run_time_limits):
                    self.test_failed += 1
                    print("test_PCA_grid_search_over_params for PCA failed: time taken to manually build models is {0}."
                          "  Maximum allowed time is {1}".format(manual_run_runtime, total_run_time_limits))
                else:
                    print("time taken to manually build all models is {0}. Maximum allowed time is "
                          "{1}".format(manual_run_runtime, total_run_time_limits))

                if self.test_failed == 0:
                    print("test_PCA_grid_search_over_params for PCA has passed!")
        except Exception as e:
            if self.possible_number_models > 0:
                print("test_PCA_grid_search_over_params for PCA failed: exception ({0}) was thrown for no reason.".format(e))
                self.test_failed += 1


def test_grid_search_for_PCA_over_all_params():
    """
    Create and instantiate class and perform tests specified for PCA

    :return: None
    """
    test_PCA_grid = Test_PCA_grid_search()
    test_PCA_grid.test_PCA_grid_search_over_params()

    sys.stdout.flush()

    if test_PCA_grid.test_failed:  # exit with error if any tests have failed
        sys.exit(1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_grid_search_for_PCA_over_all_params)
else:
    test_grid_search_for_PCA_over_all_params()
