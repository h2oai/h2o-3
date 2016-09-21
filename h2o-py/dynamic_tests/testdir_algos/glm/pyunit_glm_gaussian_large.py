from __future__ import print_function

import sys
import random
import os
import numpy as np
import scipy
import math
from scipy import stats
from builtins import range
import time

sys.path.insert(1, "../../../")

import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch


class TestGLMGaussian:
    """
    This class is created to test the GLM algo with Gaussian family.  In this case, the relationship
    between the response Y and the predictor vector X is assumed to be Y = W^T * X + E where E is
    unknown Gaussian noise.  We generate random data set using the exact formula.  Since we know
    what the W is and there are theoretical solutions to calculating W, p-values, we know the solution
     to W/p-values/MSE for test and training data set for each randomly generated
    data set.  Hence, we are able to evaluate the H2O GLM Model generated using the same random data
    sets.  When regularization and other parameters are enabled, theoretical solutions are no longer
    available.  However, we can still evaluate H2O GLM model performance by comparing the MSE from H2O model
    and to the theoretical limits since they all are using the same data sets.  As long as they do not
    deviate too much, we consider the H2O model performance satisfactory.  In particular, I have
    written 8 tests in the hope to exercise as many parameters settings of the GLM algo with Gaussian
    distribution as possible.  Tomas has requested 2 tests to be added to test his new feature of
    missing_values_handling for predictors with both categorical/real columns.  Here is a list of
    all tests:

    test1_glm_and_theory(): theoretical values for weights, p-values and MSE are calculated.
        H2O GLM model is built Gaussian family with the same random data set.  We compare
        the weights, p-values, MSEs generated from H2O with theory.
    test2_glm_lambda_search(): test lambda search with alpha set to 0.5 per Tomas's
        suggestion.  Make sure MSEs generated here is comparable in value to H2O
        GLM model with no regularization.
    test3_glm_grid_search_over_params(): test grid search with over
        various alpha values while lambda is set to be the best value obtained
        from test 2.  The best model performance hopefully will generate MSEs
        close to H2O with no regularization in test 1.
    test4_glm_remove_collinear_columns(): test parameter remove_collinear_columns=True
        with lambda set to best lambda from test 2, alpha set to best alpha from Gridsearch
        and solver set to the one which generate the smallest test MSEs.  The same data set
        is used here except that we randomly choose predictor columns to repeat and scale.
        Make sure MSEs generated here is comparable in value to H2O GLM with no
        regularization.
    test5_missing_values(): Test parameter missing_values_handling="MeanImputation" with
        only real value predictors.  The same data sets as before is used.  However, we
        go into the predictor matrix and randomly decide to change a value to be
        nan and create missing values.  Since no regularization is enabled in this case,
        we are able to calculate a theoretical weight/p-values/MSEs where we can
        compare our H2O models with.
    test6_enum_missing_values(): Test parameter missing_values_handling="MeanImputation" with
        mixed predictors (categorical/real value columns).  We first generate a data set that
        contains a random number of columns of categorical and real value columns.  Next, we
        encode the categorical columns.  Then, we generate the random data set using the formula
        Y = W^T * X+ E as before.  Next, we go into the predictor matrix (before encoding) and randomly
        decide to change a value to be nan and create missing values.  Since no regularization
        is enabled in this case, we are able to calculate a theoretical weight/p-values/MSEs
        where we can compare our H2O models with.
    test7_missing_enum_values_lambda_search(): Test parameter
        missing_values_handling="MeanImputation" with mixed predictors (categorical/real value columns).
        We first generate a data set that contains a random number of columns of categorical and real
        value columns.  Next, we encode the categorical columns using true one hot encoding.  Then,
        we generate the random data set using the formula Y = W^T * X+ E as before.  Next, we go into
        the predictor matrix (before encoding) and randomly  decide to change a value to be nan and
        create missing values.  Lambda-search will be enabled with alpha set to 0.5.  Since the
        encoding is different in this case than in test6, we will compute a theoretical weights/MSEs
        and compare the best H2O model MSEs with theoretical calculations and hope that they are close.
    """

    # parameters set by users, change with care
    max_col_count = 100         # set maximum values of train/test row and column counts
    max_col_count_ratio = 200   # set max row count to be multiples of col_count to avoid over fitting
    min_col_count_ratio = 50    # set min row count to be multiples of col_count to avoid over fitting

    ###### for debugging
#    max_col_count = 5         # set maximum values of train/test row and column counts
#    max_col_count_ratio = 50   # set max row count to be multiples of col_count to avoid overfitting
#    min_col_count_ratio = 10


    max_p_value = 50            # set maximum predictor value
    min_p_value = -50            # set minimum predictor value

    max_w_value = 50             # set maximum weight value
    min_w_value = -50            # set minimum weight value

    enum_levels = 5             # maximum number of levels for categorical variables not counting NAs

    family = 'gaussian'         # this test is for Gaussian GLM
    curr_time = str(round(time.time()))

    # parameters denoting filenames of interested that store training/validation/test data sets in csv format
    training_filename = family+"_"+curr_time+"_training_set.csv"
    training_filename_duplicate = family+"_"+curr_time+"_training_set_duplicate.csv"
    training_filename_nans = family+"_"+curr_time+"_training_set_NA.csv"
    training_filename_enum = family+"_"+curr_time+"_training_set_enum.csv"
    training_filename_enum_true_one_hot = family+"_"+curr_time+"_training_set_enum_trueOneHot.csv"
    training_filename_enum_nans = family+"_"+curr_time+"_training_set_enum_NAs.csv"
    training_filename_enum_nans_true_one_hot = family+"_"+curr_time+"_training_set_enum_NAs_trueOneHot.csv"

    validation_filename = family+"_"+curr_time+"_validation_set.csv"
    validation_filename_enum = family+"_"+curr_time+"_validation_set_enum.csv"
    validation_filename_enum_true_one_hot = family+"_"+curr_time+"_validation_set_enum_trueOneHot.csv"
    validation_filename_enum_nans = family+"_"+curr_time+"_validation_set_enum_NAs.csv"
    validation_filename_enum_nans_true_one_hot = family+"_"+curr_time+"_validation_set_enum_NAs_trueOneHot.csv"

    test_filename = family+"_"+curr_time+"_test_set.csv"
    test_filename_duplicate = family+"_"+curr_time+"_test_set_duplicate.csv"
    test_filename_nans = family+"_"+curr_time+"_test_set_NA.csv"
    test_filename_enum = family+"_"+curr_time+"_test_set_enum.csv"
    test_filename_enum_true_one_hot = family+"_"+curr_time+"_test_set_enum_trueOneHot.csv"
    test_filename_enum_nans = family+"_"+curr_time+"_test_set_enum_NAs.csv"
    test_filename_enum_nans_true_one_hot = family+"_"+curr_time+"_test_set_enum_NAs_trueOneHot.csv"

    weight_filename = family+"_"+curr_time+"_weight.csv"
    weight_filename_enum = family+"_"+curr_time+"_weight_enum.csv"

    total_test_number = 7   # total number of tests being run for GLM Gaussian family

    ignored_eps = 1e-15   # if p-values < than this value, no comparison is performed
    allowed_diff = 1e-5   # value of p-values difference allowed between theoretical and h2o p-values

    duplicate_col_counts = 5    # maximum number of times to duplicate a column
    duplicate_threshold = 0.8   # for each column, a coin is tossed to see if we duplicate that column or not
    duplicate_max_scale = 2     # maximum scale factor for duplicated columns
    nan_fraction = 0.2          # denote maximum fraction of NA's to be inserted into a column

    # System parameters, do not change.  Dire consequences may follow if you do
    current_dir = os.path.dirname(os.path.realpath(sys.argv[1]))    # directory of this test file

    enum_col = 0                # set maximum number of categorical columns in predictor
    enum_level_vec = []         # vector containing number of levels for each categorical column

    noise_std = 0.01            # noise variance in Gaussian noise generation added to response
    noise_var = noise_std*noise_std     # Gaussian noise variance

    train_row_count = 0         # training data row count, randomly generated later
    train_col_count = 0         # training data column count, randomly generated later

    data_type = 2               # determine data type of data set and weight, 1: integers, 2: real

    # parameters denoting filenames with absolute paths
    training_data_file = os.path.join(current_dir, training_filename)
    training_data_file_duplicate = os.path.join(current_dir, training_filename_duplicate)
    training_data_file_nans = os.path.join(current_dir, training_filename_nans)
    training_data_file_enum = os.path.join(current_dir, training_filename_enum)
    training_data_file_enum_true_one_hot = os.path.join(current_dir, training_filename_enum_true_one_hot)
    training_data_file_enum_nans = os.path.join(current_dir, training_filename_enum_nans)
    training_data_file_enum_nans_true_one_hot = os.path.join(current_dir, training_filename_enum_nans_true_one_hot)

    validation_data_file = os.path.join(current_dir, validation_filename)
    validation_data_file_enum = os.path.join(current_dir, validation_filename_enum)
    validation_data_file_enum_true_one_hot = os.path.join(current_dir, validation_filename_enum_true_one_hot)
    validation_data_file_enum_nans = os.path.join(current_dir, validation_filename_enum_nans)
    validation_data_file_enum_nans_true_one_hot = os.path.join(current_dir, validation_filename_enum_nans_true_one_hot)

    test_data_file = os.path.join(current_dir, test_filename)
    test_data_file_duplicate = os.path.join(current_dir, test_filename_duplicate)
    test_data_file_nans = os.path.join(current_dir, test_filename_nans)
    test_data_file_enum = os.path.join(current_dir, test_filename_enum)
    test_data_file_enum_true_one_hot = os.path.join(current_dir, test_filename_enum_true_one_hot)
    test_data_file_enum_nans = os.path.join(current_dir, test_filename_enum_nans)
    test_data_file_enum_nans_true_one_hot = os.path.join(current_dir, test_filename_enum_nans_true_one_hot)

    weight_data_file = os.path.join(current_dir, weight_filename)
    weight_data_file_enum = os.path.join(current_dir, weight_filename_enum)

    test_failed = 0             # count total number of tests that have failed
    test_failed_array = [0]*total_test_number   # denote test results for all tests run.  1 error, 0 pass
    test_num = 0                # index representing which test is being run

    duplicate_col_indices = []   # denote column indices when column duplication is applied
    duplicate_col_scales = []    # store scaling factor for all columns when duplication is applied

    # store some model performance values for later comparison for test1
    test1_r2_train = 0
    test1_mse_train = 0
    test1_weight = []
    test1_p_values = []

    test1_r2_test = 0
    test1_mse_test = 0

    test1_mse_train_theory = 0
    test1_weight_theory = []
    test1_p_values_theory = []
    test1_mse_test_theory = 0

    best_lambda = 0.0   # store best lambda obtained from lambda search

    test_name = "pyunit_glm_gaussian.py"     # name of this test
    sandbox_dir = ""  # sandbox directory where we are going to save our failed test data sets

    # store information about training, validation and test data sets that are used
    # by many tests.  We do not want to keep loading them for each set in the hope of
    # saving time.  Trading off memory and speed here.
    x_indices = []      # store predictor indices in the data set
    y_index = []        # store response index in the data set

    training_data = []  # store training data set
    test_data = []      # store test data set
    valid_data = []     # store validation data set
    training_data_grid = []     # store combined training and validation data set for cross validation for grid search

    best_alpha = -1     # store best alpha value found
    best_grid_mse = -1   # store lowest MSE found from grid search

    def __init__(self):
        self.setup()

    def setup(self):
        """
        This function performs all initializations necessary to test the GLM algo for Gaussian family:
        1. generates all the random values for our dynamic tests like the Gaussian
        noise std, column count and row count for training/validation/test data sets;
        2. generate the training/validation/test data sets with only real values;
        3. insert missing values into training/valid/test data sets.
        4. taken the training/valid/test data sets, duplicate random certain columns,
            a random number of times and randomly scale each duplicated column;
        5. generate the training/validation/test data sets with predictors containing enum
            and real values as well***.
        6. insert missing values into the training/validation/test data sets with predictors
            containing enum and real values as well

        *** according to Tomas, when working with mixed predictors (contains both enum/real
        value columns), the encoding used is different when regularization is enabled or disabled.
        When regularization is enabled, true one hot encoding is enabled to encode the enum
        values to binary bits.  When regularization is disabled, a reference level plus one hot encoding
        is enabled when encoding the enum values to binary bits.  Hence, two data sets are generated
        when we work with mixed predictors.  One with true-one-hot set to False for no regularization
        and one with true-one-hot set to True when regularization is enabled.
        """
        # clean out the sandbox directory first
        self.sandbox_dir = pyunit_utils.make_Rsandbox_dir(self.current_dir, self.test_name, True)

        # randomly set Gaussian noise standard deviation as a fraction of actual predictor standard deviation
        self.noise_std = random.uniform(0, math.sqrt(pow((self.max_p_value - self.min_p_value), 2) / 12))
        self.noise_var = self.noise_std*self.noise_std

        # randomly determine data set size in terms of column and row counts
        self.train_col_count = random.randint(3, self.max_col_count)    # accounts for enum columns later
        self.train_row_count = round(self.train_col_count * random.uniform(self.min_col_count_ratio,
                                                                           self.max_col_count_ratio))

        #  DEBUGGING setup_data, remember to comment them out once done.
        # self.train_col_count = 3
        # self.train_row_count = 200
        # end DEBUGGING

        # randomly set number of enum and real columns in the data set, we will have at least one real column
        self.enum_col = random.randint(1, self.train_col_count-1)

        # randomly set maximum enum value for each categorical column
        self.enum_level_vec = np.random.random_integers(2, self.enum_levels-1, [self.enum_col, 1])

        # generate real value weight vector and training/validation/test data set for GLM
        pyunit_utils.write_syn_floating_point_dataset_glm(self.training_data_file, self.validation_data_file,
                                                          self.test_data_file, self.weight_data_file,
                                                          self.train_row_count, self.train_col_count, self.data_type,
                                                          self.max_p_value, self.min_p_value, self.max_w_value,
                                                          self.min_w_value, self.noise_std, self.family,
                                                          self.train_row_count, self.train_row_count)

        # randomly generate the duplicated and scaled columns
        (self.duplicate_col_indices, self.duplicate_col_scales) = \
            pyunit_utils.random_col_duplication(self.train_col_count, self.duplicate_threshold,
                                                self.duplicate_col_counts, True, self.duplicate_max_scale)

        # apply the duplication and scaling to training and test set
        # need to add the response column to the end of duplicated column indices and scale
        dup_col_indices = self.duplicate_col_indices
        dup_col_indices.append(self.train_col_count)
        dup_col_scale = self.duplicate_col_scales
        dup_col_scale.append(1.0)

        # print out duplication information for easy debugging
        print("duplication column and duplication scales are: ")
        print(dup_col_indices)
        print(dup_col_scale)

        pyunit_utils.duplicate_scale_cols(dup_col_indices, dup_col_scale, self.training_data_file,
                                          self.training_data_file_duplicate)
        pyunit_utils.duplicate_scale_cols(dup_col_indices, dup_col_scale, self.test_data_file,
                                          self.test_data_file_duplicate)

        # insert NAs into training/test data sets
        pyunit_utils.insert_nan_in_data(self.training_data_file, self.training_data_file_nans, self.nan_fraction)
        pyunit_utils.insert_nan_in_data(self.test_data_file, self.test_data_file_nans, self.nan_fraction)

        # generate data sets with enum as well as real values
        pyunit_utils.write_syn_mixed_dataset_glm(self.training_data_file_enum,
                                                 self.training_data_file_enum_true_one_hot,
                                                 self.validation_data_file_enum,
                                                 self.validation_data_file_enum_true_one_hot, self.test_data_file_enum,
                                                 self.test_data_file_enum_true_one_hot, self.weight_data_file_enum,
                                                 self.train_row_count, self.train_col_count, self.max_p_value,
                                                 self.min_p_value, self.max_w_value, self.min_w_value, self.noise_std,
                                                 self.family, self.train_row_count, self.train_row_count,
                                                 self.enum_col, self.enum_level_vec)

        # insert NAs into data set with categorical columns
        pyunit_utils.insert_nan_in_data(self.training_data_file_enum, self.training_data_file_enum_nans,
                                        self.nan_fraction)
        pyunit_utils.insert_nan_in_data(self.validation_data_file_enum, self.validation_data_file_enum_nans,
                                        self.nan_fraction)
        pyunit_utils.insert_nan_in_data(self.test_data_file_enum, self.test_data_file_enum_nans, self.nan_fraction)

        pyunit_utils.insert_nan_in_data(self.training_data_file_enum_true_one_hot,
                                        self.training_data_file_enum_nans_true_one_hot, self.nan_fraction)
        pyunit_utils.insert_nan_in_data(self.validation_data_file_enum_true_one_hot,
                                        self.validation_data_file_enum_nans_true_one_hot, self.nan_fraction)
        pyunit_utils.insert_nan_in_data(self.test_data_file_enum_true_one_hot,
                                        self.test_data_file_enum_nans_true_one_hot, self.nan_fraction)

        # only preload data sets that will be used for multiple tests
        self.training_data = h2o.import_file(pyunit_utils.locate(self.training_data_file))

        # set indices for response and predictor columns in data set for H2O GLM model to use
        self.y_index = self.training_data.ncol-1
        self.x_indices = list(range(self.y_index))

        self.valid_data = h2o.import_file(pyunit_utils.locate(self.validation_data_file))
        self.test_data = h2o.import_file(pyunit_utils.locate(self.test_data_file))

        # make a bigger training set by combining data from validation data set
        self.training_data_grid = self.training_data.rbind(self.valid_data)

        # save the training data files just in case the code crashed.
        pyunit_utils.remove_csv_files(self.current_dir, ".csv", action='copy', new_dir_path=self.sandbox_dir)

    def teardown(self):
        """
        This function performs teardown after the dynamic test is completed.  If all tests
        passed, it will delete all data sets generated since they can be quite large.  It
        will move the training/validation/test data sets into a Rsandbox directory so that
        we can re-run the failed test.
        """

        remove_files = []

        # create Rsandbox directory to keep data sets and weight information
        self.sandbox_dir = pyunit_utils.make_Rsandbox_dir(self.current_dir, self.test_name, True)

        # Do not want to save all data sets.  Only save data sets that are needed for failed tests
        if sum(self.test_failed_array[0:4]):
            pyunit_utils.move_files(self.sandbox_dir, self.training_data_file, self.training_filename)
            pyunit_utils.move_files(self.sandbox_dir, self.validation_data_file, self.validation_filename)
            pyunit_utils.move_files(self.sandbox_dir, self.test_data_file, self.test_filename)
        else:   # remove those files instead of moving them
            remove_files.append(self.training_data_file)
            remove_files.append(self.validation_data_file)
            remove_files.append(self.test_data_file)

        if sum(self.test_failed_array[0:6]):
            pyunit_utils.move_files(self.sandbox_dir, self.weight_data_file, self.weight_filename)
        else:
            remove_files.append(self.weight_data_file)

        if self.test_failed_array[3]:
            pyunit_utils.move_files(self.sandbox_dir, self.training_data_file, self.training_filename)
            pyunit_utils.move_files(self.sandbox_dir, self.test_data_file, self.test_filename)
            pyunit_utils.move_files(self.sandbox_dir, self.test_data_file_duplicate, self.test_filename_duplicate)
            pyunit_utils.move_files(self.sandbox_dir, self.training_data_file_duplicate,
                                    self.training_filename_duplicate)
        else:
            remove_files.append(self.training_data_file_duplicate)
            remove_files.append(self.test_data_file_duplicate)

        if self.test_failed_array[4]:
            pyunit_utils.move_files(self.sandbox_dir, self.training_data_file, self.training_filename)
            pyunit_utils.move_files(self.sandbox_dir, self.test_data_file, self.test_filename)
            pyunit_utils.move_files(self.sandbox_dir, self.training_data_file_nans, self.training_filename_nans)
            pyunit_utils.move_files(self.sandbox_dir, self.test_data_file_nans, self.test_filename_nans)
        else:
            remove_files.append(self.training_data_file_nans)
            remove_files.append(self.test_data_file_nans)

        if self.test_failed_array[5]:
            pyunit_utils.move_files(self.sandbox_dir, self.training_data_file_enum_nans,
                                    self.training_filename_enum_nans)
            pyunit_utils.move_files(self.sandbox_dir, self.test_data_file_enum_nans, self.test_filename_enum_nans)
            pyunit_utils.move_files(self.sandbox_dir, self.weight_data_file_enum, self.weight_filename_enum)
        else:
            remove_files.append(self.training_data_file_enum_nans)
            remove_files.append(self.training_data_file_enum)
            remove_files.append(self.test_data_file_enum_nans)
            remove_files.append(self.test_data_file_enum)
            remove_files.append(self.validation_data_file_enum_nans)
            remove_files.append(self.validation_data_file_enum)
            remove_files.append(self.weight_data_file_enum)

        if self.test_failed_array[6]:
            pyunit_utils.move_files(self.sandbox_dir, self.training_data_file_enum_nans_true_one_hot,
                                    self.training_filename_enum_nans_true_one_hot)
            pyunit_utils.move_files(self.sandbox_dir, self.validation_data_file_enum_nans_true_one_hot,
                                    self.validation_filename_enum_nans_true_one_hot)
            pyunit_utils.move_files(self.sandbox_dir, self.test_data_file_enum_nans_true_one_hot,
                                    self.test_filename_enum_nans_true_one_hot)
            pyunit_utils.move_files(self.sandbox_dir, self.weight_data_file_enum, self.weight_filename_enum)
        else:
            remove_files.append(self.training_data_file_enum_nans_true_one_hot)
            remove_files.append(self.training_data_file_enum_true_one_hot)
            remove_files.append(self.validation_data_file_enum_nans_true_one_hot)
            remove_files.append(self.validation_data_file_enum_true_one_hot)
            remove_files.append(self.test_data_file_enum_nans_true_one_hot)
            remove_files.append(self.test_data_file_enum_true_one_hot)

        if not(self.test_failed):   # all tests have passed.  Delete sandbox if if was not wiped before
            pyunit_utils.make_Rsandbox_dir(self.current_dir, self.test_name, False)

        # remove any csv files left in test directory, do not remove them, shared computing resources
        if len(remove_files) > 0:
            for file in remove_files:
                pyunit_utils.remove_files(file)


    def test1_glm_and_theory(self):
        """
        This test is used to test the p-value/linear intercept weight calculation of our GLM
        when family is set to Gaussian.  Since theoretical values are available, we will compare
        our GLM output with the theoretical outputs.  This will provide assurance that our GLM
        is implemented correctly.
        """

        print("*******************************************************************************************")
        print("Test1: compares the linear regression weights/p-values computed from theory and H2O GLM.")

        try:
            # get theoretical weights, p-values and mse
            (self.test1_weight_theory, self.test1_p_values_theory, self.test1_mse_train_theory,
            self.test1_mse_test_theory) = self.theoretical_glm(self.training_data_file, self.test_data_file,
                                                               False, False)
        except:
            print("problems with lin-alg.  Got bad data set.")
            sys.exit(0)

        # get H2O model
        model_h2o = H2OGeneralizedLinearEstimator(family=self.family, Lambda=0, compute_p_values=True,
                                                  standardize=False)
        model_h2o.train(x=self.x_indices, y=self.y_index, training_frame=self.training_data)

        # calculate test metrics
        h2o_model_test_metrics = model_h2o.model_performance(test_data=self.test_data)

        num_test_failed = self.test_failed  # used to determine if current test has failed

        # print out comparison results for weight/p-values/training and test MSEs for theoretical and our H2O GLM
        (self.test1_weight, self.test1_p_values, self.test1_mse_train, self.test1_r2_train, self.test1_mse_test,
         self.test1_r2_test, self.test_failed) = \
            pyunit_utils.extract_comparison_attributes_and_print(model_h2o, h2o_model_test_metrics, "\nTest1 Done!",
                                                                 True, True, True, self.test1_weight_theory,
                                                                 self.test1_p_values_theory,
                                                                 self.test1_mse_train_theory,
                                                                 self.test1_mse_test_theory,
                                                                 "Comparing intercept and weights ....",
                                                                 "H2O intercept and weights: ",
                                                                 "Theoretical intercept and weights: ",
                                                                 "Intercept and weights are not equal!",
                                                                 "Intercept and weights are close enough!",
                                                                 "Comparing p-values ....", "H2O p-values: ",
                                                                 "Theoretical p-values: ", "P-values are not equal!",
                                                                 "P-values are close enough!",
                                                                 "Comparing training MSEs ....", "H2O training MSE: ",
                                                                 "Theoretical training MSE: ",
                                                                 "Training MSEs are not equal!",
                                                                 "Training MSEs are close enough!",
                                                                 "Comparing test MSEs ....", "H2O test MSE: ",
                                                                 "Theoretical test MSE: ", "Test MSEs are not equal!",
                                                                 "Test MSEs are close enough!", self.test_failed,
                                                                 self.ignored_eps, self.allowed_diff,
                                                                 self.noise_var, False)

        # print out test results and update test_failed_array status to reflect if this test has failed
        self.test_failed_array[self.test_num] += pyunit_utils.show_test_results("test1_glm_and_theory",
                                                                                num_test_failed, self.test_failed)
        self.test_num += 1      # update test index

    def test2_glm_lambda_search(self):
        """
        This test is used to test the lambda search.  Recall that lambda search enables efficient and
        automatic search for the optimal value of the lambda parameter.  When lambda search is enabled,
        GLM will first fit a model with maximum regularization and then keep decreasing it until
        over fitting occurs.  The resulting model is based on the best lambda value.  According to Tomas,
        set alpha = 0.5 and enable validation but not cross-validation.
        """
        print("*******************************************************************************************")
        print("Test2: tests the lambda search.")

        # generate H2O model with lambda search enabled
        model_h2o_0p5 = H2OGeneralizedLinearEstimator(family=self.family, lambda_search=True, alpha=0.5,
                                                      lambda_min_ratio=1e-20)
        model_h2o_0p5.train(x=self.x_indices, y=self.y_index, training_frame=self.training_data,
                            validation_frame=self.valid_data)

        # get best lambda here
        self.best_lambda = pyunit_utils.get_train_glm_params(model_h2o_0p5, 'best_lambda')

        # get test performance here
        h2o_model_0p5_test_metrics = model_h2o_0p5.model_performance(test_data=self.test_data)

        num_test_failed = self.test_failed

        (_, _, _, _, _, _, self.test_failed) =\
            pyunit_utils.extract_comparison_attributes_and_print(model_h2o_0p5, h2o_model_0p5_test_metrics,
                                                                 "\nTest2 Done!", False, False, False,
                                                                 self.test1_weight, None, self.test1_mse_train,
                                                                 self.test1_mse_test,
                                                                 "Comparing intercept and weights ....",
                                                                 "H2O lambda search intercept and weights: ",
                                                                 "H2O test1 template intercept and weights: ",
                                                                 "Intercept and weights are not equal!",
                                                                 "Intercept and weights are close enough!", "", "", "",
                                                                 "", "", "Comparing training MSEs ....",
                                                                 "H2O lambda search training MSE: ",
                                                                 "H2O Test1 template training MSE: ",
                                                                 "Training MSEs are not equal!",
                                                                 "Training MSEs are close enough!",
                                                                 "Comparing test MSEs ....",
                                                                 "H2O lambda search test MSE: ",
                                                                 "H2O Test1 template test MSE: ",
                                                                 "Test MSEs are not equal!",
                                                                 "Test MSEs are close enough!", self.test_failed,
                                                                 self.ignored_eps, self.allowed_diff, self.noise_var,
                                                                 True)

        # print out test results and update test_failed_array status to reflect if this test has failed
        self.test_failed_array[self.test_num] += pyunit_utils.show_test_results("test2_glm_lambda_search",
                                                                                num_test_failed, self.test_failed)
        self.test_num += 1

    def test3_glm_grid_search(self):
        """
        This test is used to test GridSearch with the following parameters:

        1. Lambda = best_lambda value from test2
        2. alpha = [0 0.5 0.99]
        3. cross-validation with nfolds = 5, fold_assignment = "Random"

        We will look at the best results from the grid search and compare it with test 1
        results.

        :return: None
        """
        print("*******************************************************************************************")
        print("Test3: explores various parameter settings in training the GLM using GridSearch using solver ")

        hyper_parameters = {'alpha': [0, 0.5, 0.99]}    # set hyper_parameters for grid search

        # train H2O GLM model with grid search
        model_h2o_grid_search = H2OGridSearch(H2OGeneralizedLinearEstimator(family=self.family, Lambda=self.best_lambda,
                                                                            nfolds=5, fold_assignment='Random'),
                                              hyper_parameters)
        model_h2o_grid_search.train(x=self.x_indices, y=self.y_index, training_frame=self.training_data_grid)

        # print out the model sequence ordered by the best MSE values, thanks Ludi!
        temp_model = model_h2o_grid_search.sort_by("mse(xval=True)")

        # obtain the model ID of best model (with smallest MSE) and use that for our evaluation
        best_model_id = temp_model['Model Id'][0]
        self.best_grid_mse = temp_model['mse(xval=True)'][0]
        self.best_alpha = model_h2o_grid_search.get_hyperparams(best_model_id)

        best_model = h2o.get_model(best_model_id)
        best_model_test_metrics = best_model.model_performance(test_data=self.test_data)

        num_test_failed = self.test_failed

        (_, _, _, _, _, _, self.test_failed) =\
            pyunit_utils.extract_comparison_attributes_and_print(best_model, best_model_test_metrics, "\nTest3 Done!",
                                                                 False, False, False, self.test1_weight, None,
                                                                 self.test1_mse_train, self.test1_mse_test,
                                                                 "Comparing intercept and weights ....",
                                                                 "H2O best model from gridsearch intercept "
                                                                 "and weights: ",
                                                                 "H2O test1 template intercept and weights: ",
                                                                 "Intercept and weights are not equal!",
                                                                 "Intercept and weights are close enough!", "", "", "",
                                                                 "", "", "Comparing training MSEs ....",
                                                                 "H2O best model from gridsearch training MSE: ",
                                                                 "H2O Test1 template training MSE: ",
                                                                 "Training MSEs are not equal!",
                                                                 "Training MSEs are close enough!",
                                                                 "Comparing test MSEs ....",
                                                                 "H2O best model from gridsearch test MSE: ",
                                                                 "H2O Test1 template test MSE: ",
                                                                 "Test MSEs are not equal!",
                                                                 "Test MSEs are close enough!", self.test_failed,
                                                                 self.ignored_eps, self.allowed_diff,
                                                                 self.noise_var, False)

        # print out test results and update test_failed_array status to reflect if this test has failed
        self.test_failed_array[self.test_num] += pyunit_utils.show_test_results("test_glm_grid_search_over_params",
                                                                                num_test_failed, self.test_failed)
        self.test_num += 1

    def test4_glm_remove_collinear_columns(self):
        """
        With the best parameters obtained from test 3 grid search, we will trained GLM
        with duplicated columns and enable remove_collinear_columns and see if the
        algorithm catches the duplicated columns.  We will compare the results with test
        1 results.
        """
        print("*******************************************************************************************")
        print("Test4: test the GLM remove_collinear_columns.")

        # read in training data sets with duplicated columns
        training_data = h2o.import_file(pyunit_utils.locate(self.training_data_file_duplicate))
        test_data = h2o.import_file(pyunit_utils.locate(self.test_data_file_duplicate))

        y_index = training_data.ncol-1
        x_indices = list(range(y_index))

        print("Best lambda is {0}, best alpha is {1}".format(self.best_lambda, self.best_alpha))
        # train H2O model with remove_collinear_columns=True
        model_h2o = H2OGeneralizedLinearEstimator(family=self.family, Lambda=self.best_lambda, alpha=self.best_alpha,
                                                  remove_collinear_columns=True)
        model_h2o.train(x=x_indices, y=y_index, training_frame=training_data)

        # evaluate model over test data set
        model_h2o_metrics = model_h2o.model_performance(test_data=test_data)

        num_test_failed = self.test_failed
        (_, _, _, _, _, _, self.test_failed) = \
            pyunit_utils.extract_comparison_attributes_and_print(model_h2o, model_h2o_metrics, "\nTest4 Done!", False,
                                                                 False, False, self.test1_weight, None,
                                                                 self.test1_mse_train, self.test1_mse_test,
                                                                 "Comparing intercept and weights ....",
                                                                 "H2O remove_collinear_columns intercept and "
                                                                 "weights: ",
                                                                 "H2O test1 template intercept and weights: ",
                                                                 "Intercept and weights are not equal!",
                                                                 "Intercept and weights are close enough!", "", "", "",
                                                                 "", "", "Comparing training MSEs ....",
                                                                 "H2O remove_collinear_columns training MSE: ",
                                                                 "H2O Test1 template training MSE: ",
                                                                 "Training MSEs are not equal!",
                                                                 "Training MSEs are close enough!",
                                                                 "Comparing test MSEs ....",
                                                                 "H2O remove_collinear_columns test MSE: ",
                                                                 "H2O Test1 template test MSE: ",
                                                                 "Test MSEs are not equal!",
                                                                 "Test MSEs are close enough!", self.test_failed,
                                                                 self.ignored_eps, self.allowed_diff, self.noise_var,
                                                                 False)

        # print out test results and update test_failed_array status to reflect if this test has failed
        self.test_failed_array[self.test_num] += pyunit_utils.show_test_results("test4_glm_remove_collinear_columns",
                                                                                num_test_failed, self.test_failed)
        self.test_num += 1

    def test5_missing_values(self):
        """
        Test parameter missing_values_handling="MeanImputation" with
        only real value predictors.  The same data sets as before are used.  However, we
        go into the predictor matrix and randomly decide to change a value to be
        nan and create missing values.  Since no regularization is enabled in this case,
        we are able to calculate a theoretical weight/p-values/MSEs where we can
        compare our H2O models with.
        """
        print("*******************************************************************************************")
        print("Test5: test the GLM with imputation of missing values with column averages.")

        # get theoretical weights, p-values and mse
        try:
            (weight_theory, p_values_theory, mse_train_theory, mse_test_theory) = \
                self.theoretical_glm(self.training_data_file_nans, self.test_data_file_nans, False, False)
        except:
            print("Bad dataset, lin-alg package problem.")
            sys.exit(0)

        # import training set and test set
        training_data = h2o.import_file(pyunit_utils.locate(self.training_data_file_nans))
        test_data = h2o.import_file(pyunit_utils.locate(self.test_data_file_nans))

        # train H2O models with missing_values_handling="MeanImputation"
        model_h2o = H2OGeneralizedLinearEstimator(family=self.family, Lambda=0, compute_p_values=True,
                                                  missing_values_handling="MeanImputation", standardize=False)
        model_h2o.train(x=self.x_indices, y=self.y_index, training_frame=training_data)

        # calculate H2O model performance with test data set
        h2o_model_test_metrics = model_h2o.model_performance(test_data=test_data)

        num_test_failed = self.test_failed
        (_, _, _, _, _, _, self.test_failed) =\
            pyunit_utils.extract_comparison_attributes_and_print(model_h2o, h2o_model_test_metrics, "\nTest5 Done!",
                                                                 True, True, True, weight_theory, p_values_theory,
                                                                 mse_train_theory, mse_test_theory,
                                                                 "Comparing intercept and weights ....",
                                                                 "H2O missing values intercept and weights: ",
                                                                 "Theoretical intercept and weights: ",
                                                                 "Intercept and weights are not equal!",
                                                                 "Intercept and weights are close enough!",
                                                                 "Comparing p-values ....",
                                                                 "H2O missing values p-values: ",
                                                                 "Theoretical p-values: ", "P-values are not equal!",
                                                                 "P-values are close enough!",
                                                                 "Comparing training MSEs ....",
                                                                 "H2O missing values training MSE: ",
                                                                 "Theoretical training MSE: ",
                                                                 "Training MSEs are not equal!",
                                                                 "Training MSEs are close enough!",
                                                                 "Comparing test MSEs ....",
                                                                 "H2O missing values test MSE: ",
                                                                 "Theoretical test MSE: ", "Test MSEs are not equal!",
                                                                 "Test MSEs are close enough!", self.test_failed,
                                                                 self.ignored_eps, self.allowed_diff, self.noise_var,
                                                                 False)

        # print out test results and update test_failed_array status to reflect if this test has failed
        self.test_failed_array[self.test_num] += pyunit_utils.show_test_results("test5_missing_values",
                                                                                num_test_failed, self.test_failed)
        self.test_num += 1

    def test6_enum_missing_values(self):
        """
        Test parameter missing_values_handling="MeanImputation" with
        mixed predictors (categorical/real value columns).  We first generate a data set that
        contains a random number of columns of categorical and real value columns.  Next, we
        encode the categorical columns.  Then, we generate the random data set using the formula
        Y = W^T * X+ E as before.  Next, we go into the predictor matrix and randomly
        decide to change a value to be nan and create missing values.  Since no regularization
        is enabled in this case, we are able to calculate a theoretical weight/p-values/MSEs
        where we can compare our H2O models with.
        """

        # no regularization in this case, use reference level plus one-hot-encoding
        print("*******************************************************************************************")
        print("Test6: test the GLM with enum/real values.")

        try:
            # get theoretical weights, p-values and mse
            (weight_theory, p_values_theory, mse_train_theory, mse_test_theory) =\
                self.theoretical_glm(self.training_data_file_enum_nans, self.test_data_file_enum_nans, True, False)
        except:
            print("Bad data set.  Problem with lin-alg.")
            sys.exit(0)

        # import training set and test set with missing values
        training_data = h2o.import_file(pyunit_utils.locate(self.training_data_file_enum_nans))
        test_data = h2o.import_file(pyunit_utils.locate(self.test_data_file_enum_nans))

        # change the categorical data using .asfactor()
        for ind in range(self.enum_col):
            training_data[ind] = training_data[ind].round().asfactor()
            test_data[ind] = test_data[ind].round().asfactor()

        num_col = training_data.ncol
        y_index = num_col-1
        x_indices = list(range(y_index))

        # generate H2O model
        model_h2o = H2OGeneralizedLinearEstimator(family=self.family, Lambda=0, compute_p_values=True,
                                                  missing_values_handling="MeanImputation")
        model_h2o.train(x=x_indices, y=y_index, training_frame=training_data)

        h2o_model_test_metrics = model_h2o.model_performance(test_data=test_data)

        num_test_failed = self.test_failed
        (_, _, _, _, _, _, self.test_failed) =\
            pyunit_utils.extract_comparison_attributes_and_print(model_h2o, h2o_model_test_metrics, "\nTest6 Done!",
                                                                 True, False, False, weight_theory, p_values_theory,
                                                                 mse_train_theory, mse_test_theory,
                                                                 "Comparing intercept and weights with enum and "
                                                                 "missing values....",
                                                                 "H2O enum missing values no regularization "
                                                                 "intercept and weights: ",
                                                                 "Theoretical intercept and weights: ",
                                                                 "Intercept and weights are not equal!",
                                                                 "Intercept and weights are close enough!",
                                                                 "Comparing p-values ....",
                                                                 "H2O enum missing values no regularization p-values: ",
                                                                 "Theoretical p-values: ", "P-values are not equal!",
                                                                 "P-values are close enough!",
                                                                 "Comparing training MSEs ....",
                                                                 "H2O enum missing values no regularization "
                                                                 "training MSE: ",
                                                                 "Theoretical training MSE: ",
                                                                 "Training MSEs are not equal!",
                                                                 "Training MSEs are close enough!",
                                                                 "Comparing test MSEs ....",
                                                                 "H2O enum missing values no regularization test MSE: ",
                                                                 "Theoretical test MSE: ", "Test MSEs are not equal!",
                                                                 "Test MSEs are close enough!", self.test_failed,
                                                                 self.ignored_eps, self.allowed_diff, self.noise_var,
                                                                 False, attr3_bool=False)

        # print out test results and update test_failed_array status to reflect if this test has failed
        self.test_failed_array[self.test_num] += pyunit_utils.show_test_results("test6_enum_missing_values",
                                                                                num_test_failed, self.test_failed)
        self.test_num += 1

    def test7_missing_enum_values_lambda_search(self):
        """
        Test parameter missing_values_handling="MeanImputation" with mixed predictors (categorical/real value columns).
        Test parameter missing_values_handling="MeanImputation" with
        mixed predictors (categorical/real value columns).  We first generate a data set that
        contains a random number of columns of categorical and real value columns.  Next, we
        encode the categorical columns.  Then, we generate the random data set using the formula
        Y = W^T * X+ E as before.  Next, we go into the predictor matrix and randomly
        decide to change a value to be nan and create missing values.  Lambda-search will be
        enabled with alpha set to 0.5.  Since the encoding is different in this case
        than in test6, we will compute a theoretical weights/MSEs and compare the best H2O
        model MSEs with theoretical calculations and hope that they are close.
        """

        # perform lambda_search, regularization and one hot encoding.
        print("*******************************************************************************************")
        print("Test7: test the GLM with imputation of missing enum/real values under lambda search.")

        try:
            # get theoretical weights, p-values and mse
            (weight_theory, p_values_theory, mse_train_theory, mse_test_theory) =\
                self.theoretical_glm(self.training_data_file_enum_nans_true_one_hot,
                                     self.test_data_file_enum_nans_true_one_hot, True, True,
                                     validation_data_file=self.validation_data_file_enum_nans_true_one_hot)
        except:
            print("Bad data set.  Problem with lin-alg.")
            sys.exit(0)

        # import training set and test set with missing values and true one hot encoding
        training_data = h2o.import_file(pyunit_utils.locate(self.training_data_file_enum_nans_true_one_hot))
        validation_data = h2o.import_file(pyunit_utils.locate(self.validation_data_file_enum_nans_true_one_hot))
        test_data = h2o.import_file(pyunit_utils.locate(self.test_data_file_enum_nans_true_one_hot))

        # change the categorical data using .asfactor()
        for ind in range(self.enum_col):
            training_data[ind] = training_data[ind].round().asfactor()
            validation_data[ind] = validation_data[ind].round().asfactor()
            test_data[ind] = test_data[ind].round().asfactor()

        num_col = training_data.ncol
        y_index = num_col-1
        x_indices = list(range(y_index))

        # train H2O model
        model_h2o_0p5 = H2OGeneralizedLinearEstimator(family=self.family, lambda_search=True, alpha=0.5,
                                                      lambda_min_ratio=1e-20, missing_values_handling="MeanImputation")
        model_h2o_0p5.train(x=x_indices, y=y_index, training_frame=training_data,
                            validation_frame=validation_data)

        h2o_model_0p5_test_metrics = model_h2o_0p5.model_performance(test_data=test_data)

        num_test_failed = self.test_failed
        (_, _, _, _, _, _, self.test_failed) =\
            pyunit_utils.extract_comparison_attributes_and_print(model_h2o_0p5, h2o_model_0p5_test_metrics,
                                                                 "\nTest7 Done!", False, False, True, weight_theory,
                                                                 None, mse_train_theory, mse_test_theory,
                                                                 "Comparing intercept and weights with categorical "
                                                                 "columns, missing values and lambda search....",
                                                                 "H2O enum missing values and lambda search "
                                                                 "intercept and weights: ",
                                                                 "Theoretical intercept and weights: ",
                                                                 "Intercept and weights are not equal!",
                                                                 "Intercept and weights are close enough!",
                                                                 "Comparing p-values ....",
                                                                 "H2O enum missing valuesand lambda search "
                                                                 "p-values: ",
                                                                 "Theoretical p-values: ", "P-values are not equal!",
                                                                 "P-values are close enough!",
                                                                 "Comparing training MSEs ....",
                                                                 "H2O enum missing values and lambda search "
                                                                 "training MSE: ",
                                                                 "Theoretical training MSE: ",
                                                                 "Training MSEs are not equal!",
                                                                 "Training MSEs are close enough!",
                                                                 "Comparing test MSEs ....",
                                                                 "H2O enum missing values and lambda search test MSE: ",
                                                                 "Theoretical test MSE: ", "Test MSEs are not equal!",
                                                                 "Test MSEs are close enough!", self.test_failed,
                                                                 self.ignored_eps, self.allowed_diff, self.noise_var,
                                                                 False, attr3_bool=False)

        # print out test results and update test_failed_array status to reflect if this test has failed
        self.test_failed_array[self.test_num] += \
            pyunit_utils.show_test_results("test7_missing_enum_values_lambda_search", num_test_failed, self.test_failed)
        self.test_num += 1

    def theoretical_glm(self, training_data_file, test_data_file, has_categorical, true_one_hot,
                        validation_data_file=""):
        """
        This function is written to load in a training/test data sets with predictors followed by the response
        as the last column.  We then calculate the weights/bias and the p-values using derived formulae
        off the web.

        :param training_data_file: string representing the training data set filename
        :param test_data_file:  string representing the test data set filename
        :param has_categorical: bool indicating if the data set contains mixed predictors (both enum and real)
        :param true_one_hot:  bool True: true one hot encoding is used.  False: reference level plus one hot
        encoding is used
        :param validation_data_file: optional string, denoting validation file so that we can concatenate
         training and validation data sets into a big training set since H2O model is using a training
         and a validation data set.

        :return: a tuple containing weights, p-values, training data set MSE and test data set MSE

        """
        # read in the training data
        training_data_xy = np.asmatrix(np.genfromtxt(training_data_file, delimiter=',', dtype=None))
        test_data_xy = np.asmatrix(np.genfromtxt(test_data_file, delimiter=',', dtype=None))

        if len(validation_data_file) > 0:    # validation data set exist and add it to training_data
            temp_data_xy = np.asmatrix(np.genfromtxt(validation_data_file, delimiter=',', dtype=None))
            training_data_xy = np.concatenate((training_data_xy, temp_data_xy), axis=0)

        # if predictor contains categorical data, perform imputation during encoding of enums to binary bits
        if has_categorical:
            training_data_xy = pyunit_utils.encode_enum_dataset(training_data_xy, self.enum_level_vec,
                                                                self.enum_col, true_one_hot, np.any(training_data_xy))
            test_data_xy = pyunit_utils.encode_enum_dataset(test_data_xy, self.enum_level_vec, self.enum_col,
                                                            true_one_hot, np.any(training_data_xy))

        # replace missing values with column mean before proceeding for training/test data sets
        if np.isnan(training_data_xy).any():
            inds = np.where(np.isnan(training_data_xy))
            col_means = stats.nanmean(training_data_xy, axis=0)
            training_data_xy[inds] = np.take(col_means, inds[1])

            if np.isnan(test_data_xy).any():
                # replace the actual means with column means from training
                inds = np.where(np.isnan(test_data_xy))
                test_data_xy = pyunit_utils.replace_nan_with_mean(test_data_xy, inds, col_means)

        (num_row, num_col) = training_data_xy.shape

        dof = num_row - num_col     # degree of freedom in t-distribution

        response_y = training_data_xy[:, num_col-1]
        training_data = training_data_xy[:, range(0, num_col-1)]

        # generate weight vector W = (X^T*X)^(-1)*X^T*Y
        # form the X matrix here
        temp_ones = np.asmatrix(np.ones(num_row)).transpose()
        x_mat = np.concatenate((temp_ones, training_data), axis=1)

        mat_inv = np.linalg.pinv(x_mat.transpose()*x_mat)
        t_weights = mat_inv*x_mat.transpose()*response_y

        # calculate training data MSE here
        t_predict_y = x_mat*t_weights
        delta = t_predict_y-response_y
        mse_train = delta.transpose()*delta

        # calculate 2-sided p-values here
        mysd = mse_train/dof
        se = np.sqrt(mysd*np.diag(mat_inv))
        tval = abs(t_weights.transpose()/se)  # ensure floating point division here
        p_values = scipy.stats.t.sf(tval, dof)*2

        # calculate test data MSE
        test_response_y = test_data_xy[:, num_col-1]
        test_data = test_data_xy[:, range(0, num_col-1)]

        t_predict = pyunit_utils.generate_response_glm(t_weights, test_data, 0, self.family)
        (num_row_t, num_col_t) = test_data.shape

        temp = t_predict-test_response_y
        mse_test = temp.transpose()*temp/num_row_t  # test data MSE

        return np.array(t_weights.transpose())[0].tolist(), np.array(p_values)[0].tolist(), mse_train[0, 0]/num_row, \
            mse_test[0, 0]


def test_glm_gaussian():
    """
    Create and instantiate TestGLMGaussian class and perform tests specified for GLM
    Gaussian family.

    :return: None
    """
    test_glm_gaussian = TestGLMGaussian()
    test_glm_gaussian.test1_glm_and_theory()
    test_glm_gaussian.test2_glm_lambda_search()
    test_glm_gaussian.test3_glm_grid_search()
    test_glm_gaussian.test4_glm_remove_collinear_columns()
    test_glm_gaussian.test5_missing_values()
    test_glm_gaussian.test6_enum_missing_values()
    test_glm_gaussian.test7_missing_enum_values_lambda_search()
    test_glm_gaussian.teardown()

    sys.stdout.flush()

    if test_glm_gaussian.test_failed:  # exit with error if any tests have failed
        sys.exit(1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_gaussian)
else:
    test_glm_gaussian()
