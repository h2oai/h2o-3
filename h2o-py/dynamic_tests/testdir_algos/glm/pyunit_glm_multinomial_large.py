from __future__ import print_function

import sys
import os
import random
import math
import numpy as np
import time

sys.path.insert(1, "../../../")

import h2o

from builtins import range
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch
from scipy import stats
from sklearn.linear_model import LogisticRegression
from sklearn import metrics


class TestGLMMultinomial:
    """
    This class is created to test the GLM algo with Multinomial family per request from Tomas as this
    is a new feature for the GLM alo.  In this case, the relationship
    between the response Y (K possible classes) and predictor vector X is assumed to be
    Prob(Y = c|X) = exp(Wc^T * X + E)/(sum k=0 to K-1 (ep(Wk^T *X+E)) where E is
    unknown Gaussian noise.  We generate random data sets using the exact formula.  To evaluate the H2O GLM Model,
    we run the sklearn logistic regression with the same data sets and compare the performance of the two.  If they
    are close enough within a certain tolerance, we declare the H2O model working.  When regularization and other
    parameters are enabled, we can evaluate H2O GLM model performance by comparing the logloss/accuracy
    from H2O model and to the H2O model generated without regularization.  As long as they do not deviate
    too much, we consider the H2O model performance satisfactory.

    In particular, I have written 7 tests in the hope to exercise
    as many parameters settings of the GLM algo with Multinomial distribution as possible.  Tomas
    has requested 2 tests to be added to test his new feature of missing_values_handling with
    predictors containing both categorical/real columns.  Here is a list of all tests:

    test1_glm_no_regularization(): sklearn logistic regression model is built.
        H2O GLM is built for Multinomial family with the same random data sets.  We observe
        the weights, confusion matrices from the two models.  We compare the logloss, prediction
        accuracy from the two models to determine if H2O GLM model shall pass the test.
    test2_glm_lambda_search(): test lambda search with alpha set to 0.5 per Tomas's
        suggestion.  Make sure loglss and prediction accuracy generated here is comparable in
        value to H2O GLM model with no regularization.
    test3_glm_grid_search_over_params(): test grid search over
        various alpha values while lambda is set to be the best value obtained
        from test 2.  Cross validation with k=5 and random assignment is enabled
        as well.  The best model performance hopefully will generate logloss and
        prediction accuracies close to H2O model with no regularization in test 1.
    test5_missing_values(): Test parameter missing_values_handling="MeanImputation" with
        only real value predictors.  The same data sets as before is used.  However, we
        go into the predictor matrix and randomly decide to replace a value with
        nan and create missing values.  Sklearn logistic regression model is built using the
        data set where we have imputed the missing values.  This Sklearn model will be used to
        compare our H2O models with.
    test6_enum_missing_values(): Test parameter missing_values_handling="MeanImputation" with
        mixed predictors (categorical/real value columns).  We first generate a data set that
        contains a random number of columns of categorical and real value columns.  Next, we
        encode the categorical columns.  Then, we generate the random data set using the formula
        as before.  Next, we go into the predictor matrix and randomly
        decide to change a value to be nan and create missing values.  Again, we build a Sklearn
        logistic regression and compare our H2O model with it.
    test7_missing_enum_values_lambda_search(): Test parameter
        missing_values_handling="MeanImputation" with mixed predictors (categorical/real value columns).
        and setting lambda search to be True.  We use the same predictors from test6.  Next, we encode
        the categorical columns using true one hot encoding since Lambda-search will be enabled with
        alpha set to 0.5.  Since the encoding is different in this case from test6, we will build a brand
        new Sklearn logistic regression model and compare the best H2O model logloss/prediction accuracy with it.
    """

    # parameters set by users, change with care
    max_col_count = 50          # set maximum values of train/test row and column counts
    max_col_count_ratio = 500   # set max row count to be multiples of col_count to avoid over fitting
    min_col_count_ratio = 100   # set min row count to be multiples of col_count to avoid over fitting

    ###### for debugging
    max_col_count = 5         # set maximum values of train/test row and column counts
    max_col_count_ratio = 50   # set max row count to be multiples of col_count to avoid overfitting
    min_col_count_ratio = 10



    max_p_value = 2             # set maximum predictor value
    min_p_value = -2            # set minimum predictor value

    max_w_value = 2             # set maximum weight value
    min_w_value = -2            # set minimum weight value

    max_class_number = 10       # maximum number of classes allowed

    class_method = 'probability'    # can be 'probability' or 'threshold', control how discrete response is generated
    test_class_method = 'probability'   # for test data set
    margin = 0.0                    # only used when class_method = 'threshold'
    test_class_margin = 0.2         # for test data set
    family = 'multinomial'         # this test is for Multinomial GLM
    curr_time = str(round(time.time()))

    # parameters denoting filenames of interested that store training/validation/test data sets
    training_filename = family+"_"+curr_time+"_training_set.csv"
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
    test_filename_nans = family+"_"+curr_time+"_test_set_NA.csv"
    test_filename_enum = family+"_"+curr_time+"_test_set_enum.csv"
    test_filename_enum_true_one_hot = family+"_"+curr_time+"_test_set_enum_trueOneHot.csv"
    test_filename_enum_nans = family+"_"+curr_time+"_test_set_enum_NAs.csv"
    test_filename_enum_nans_true_one_hot = family+"_"+curr_time+"_test_set_enum_NAs_trueOneHot.csv"

    weight_filename = family+"_"+curr_time+"_weight.csv"
    weight_filename_enum = family+"_"+curr_time+"_weight_enum.csv"

    # number of tests run for GLM Multinomial family, only 7.  Use 8 to use tear_down
    #  of other distribution families
    total_test_number = 7

    ignored_eps = 1e-15   # if p-values < than this value, no comparison is performed, only for Gaussian
    allowed_diff = 1e-1   # tolerance of comparison for logloss/prediction accuracy

    nan_fraction = 0.2          # denote maximum fraction of NA's to be inserted into a column

    # System parameters, do not change.  Dire consequences may follow if you do
    current_dir = os.path.dirname(os.path.realpath(sys.argv[1]))    # directory of this test file

    enum_col = 0                # set maximum number of categorical columns in predictor
    enum_level_vec = []         # vector containing number of levels for each categorical column

    noise_std = 0               # noise variance in Multinomial noise generation added to response

    train_row_count = 0         # training data row count, randomly generated later
    train_col_count = 0         # training data column count, randomly generated later

    class_number = 0            # actual number of classification classes in data set, randomly generated later

    data_type = 2               # determine data type of data set and weight, 1: integers, 2: real

    enum_levels = 5             # maximum number of levels for categorical variables not counting NAs

    # parameters denoting filenames with absolute paths
    training_data_file = os.path.join(current_dir, training_filename)
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
    test_data_file_nans = os.path.join(current_dir, test_filename_nans)
    test_data_file_enum = os.path.join(current_dir, test_filename_enum)
    test_data_file_enum_true_one_hot = os.path.join(current_dir, test_filename_enum_true_one_hot)
    test_data_file_enum_nans = os.path.join(current_dir, test_filename_enum_nans)
    test_data_file_enum_nans_true_one_hot = os.path.join(current_dir, test_filename_enum_nans_true_one_hot)

    weight_data_file = os.path.join(current_dir, weight_filename)
    weight_data_file_enum = os.path.join(current_dir, weight_filename_enum)

    # store template model performance values for later comparison
    test_template_model = None          # store template model for later comparison
    test_template_model_metrics = None  # store template model test metrics for later comparison

    best_lambda = 0.0   # store best lambda obtained using lambda search

    test_name = "pyunit_glm_multinomial.py"     # name of this test
    sandbox_dir = ""    # sandbox directory where we are going to save our failed test data sets

    # store information about training, validation and test data sets that are used
    # by many tests.  We do not want to keep loading them for each set in the hope of
    # saving time.  Trading off memory and speed here.
    x_indices = []    # store predictor indices in the data set
    y_index = []      # store response index in the data set

    training_data = []  # store training data set
    test_data = []      # store test data set
    valid_data = []     # store validation data set
    training_data_grid = []     # store combined training and validation data set for cross validation

    best_alpha = -1     # store best alpha value found
    best_grid_logloss = -1   # store lowest logloss found from grid search

    test_failed_array = [0]*total_test_number   # denote test results for all tests run.  1 error, 0 pass
    test_num = 0                # index representing which test is being run

    duplicate_col_indices = []      # denote column indices when column duplication is applied
    duplicate_col_scales = []       # store scaling factor for all columns when duplication is applied

    noise_var = noise_std*noise_std     # Multinomial noise variance

    test_failed = 0             # count total number of tests that have failed

    sklearn_class_weight = {}   # used to make sure Sklearn will know the correct number of classes

    def __init__(self):
        self.setup()

    def setup(self):
        """
        This function performs all initializations necessary:
        1. generates all the random values for our dynamic tests like the Multinomial
        noise std, column count and row count for data sets;
        2. generate the training/validation/test data sets with only real values;
        3. insert missing values into training/valid/test data sets.
        4. take the training/valid/test data sets, duplicate random certain columns,
            a random number of times and randomly scale each duplicated column;
        5. generate the training/validation/test data sets with predictors containing enum
            and real values as well***
        6. insert missing values into the training/validation/test data sets with predictors
            containing enum and real values as well

        It was observed that the program actually crash before the tests are completed.  Hence,
        with multinomial, we are going to save all training data into the sandbox directory.
        If all tests are passed, the csv files will be deleted if tear_down is called.

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

        # randomly set Multinomial noise standard deviation as a fraction of actual predictor standard deviation
        self.noise_std = random.uniform(0, math.sqrt(pow((self.max_p_value - self.min_p_value), 2) / 12))
        self.noise_var = self.noise_std*self.noise_std

        # randomly determine data set size in terms of column and row counts
        self.train_col_count = random.randint(3, self.max_col_count)    # accounts for enum columns later
        self.train_row_count = round(self.train_col_count*random.uniform(self.min_col_count_ratio,
                                                                         self.max_col_count_ratio))

        self.class_number = random.randint(3, self.max_class_number)    # randomly set number of classes K

        # DEBUGGING setup_data, remember to comment them out once done.
        # self.train_col_count = 3
        # self.train_row_count = 500
        # self.class_number = 3
        # # end DEBUGGING

        # randomly set number of enum and real columns in the data set
        self.enum_col = random.randint(1, self.train_col_count-1)

        # randomly set number of levels for each categorical column
        self.enum_level_vec = np.random.random_integers(2, self.enum_levels-1, [self.enum_col, 1])

        # generate real value weight vector and training/validation/test data set for GLM
        pyunit_utils.write_syn_floating_point_dataset_glm(self.training_data_file, self.validation_data_file,
                                                          self.test_data_file, self.weight_data_file,
                                                          self.train_row_count, self.train_col_count, self.data_type,
                                                          self.max_p_value, self.min_p_value, self.max_w_value,
                                                          self.min_w_value, self.noise_std, self.family,
                                                          self.train_row_count, self.train_row_count,
                                                          class_number=self.class_number,
                                                          class_method=[
                                                              self.class_method, self.class_method,
                                                              self.test_class_method],
                                                          class_margin=[
                                                              self.margin, self.margin, self.test_class_margin])

        # insert NAs into training/test data sets
        pyunit_utils.insert_nan_in_data(self.training_data_file, self.training_data_file_nans, self.nan_fraction)
        pyunit_utils.insert_nan_in_data(self.test_data_file, self.test_data_file_nans, self.nan_fraction)

        # generate data sets with enum as well as real values
        pyunit_utils.write_syn_mixed_dataset_glm(self.training_data_file_enum,
                                                 self.training_data_file_enum_true_one_hot,
                                                 self.validation_data_file_enum,
                                                 self.validation_data_file_enum_true_one_hot,
                                                 self.test_data_file_enum, self.test_data_file_enum_true_one_hot,
                                                 self.weight_data_file_enum, self.train_row_count, self.train_col_count,
                                                 self.max_p_value, self.min_p_value, self.max_w_value, self.min_w_value,
                                                 self.noise_std, self.family, self.train_row_count,
                                                 self.train_row_count, self.enum_col, self.enum_level_vec,
                                                 class_number=self.class_number,
                                                 class_method=[
                                                     self.class_method, self.class_method, self.test_class_method],
                                                 class_margin=[self.margin, self.margin, self.test_class_margin])

        # insert NAs into data set with categorical columns
        pyunit_utils.insert_nan_in_data(self.training_data_file_enum, self.training_data_file_enum_nans,
                                        self.nan_fraction)
        pyunit_utils.insert_nan_in_data(self.validation_data_file_enum, self.validation_data_file_enum_nans,
                                        self.nan_fraction)
        pyunit_utils.insert_nan_in_data(self.test_data_file_enum, self.test_data_file_enum_nans,
                                        self.nan_fraction)

        pyunit_utils.insert_nan_in_data(self.training_data_file_enum_true_one_hot,
                                        self.training_data_file_enum_nans_true_one_hot, self.nan_fraction)
        pyunit_utils.insert_nan_in_data(self.validation_data_file_enum_true_one_hot,
                                        self.validation_data_file_enum_nans_true_one_hot, self.nan_fraction)
        pyunit_utils.insert_nan_in_data(self.test_data_file_enum_true_one_hot,
                                        self.test_data_file_enum_nans_true_one_hot, self.nan_fraction)

        # only preload data sets that will be used for multiple tests and change the response to enums
        self.training_data = h2o.import_file(pyunit_utils.locate(self.training_data_file))

        # set indices for response and predictor columns in data set for H2O GLM model to use
        self.y_index = self.training_data.ncol-1
        self.x_indices = list(range(self.y_index))

        self.training_data[self.y_index] = self.training_data[self.y_index].round().asfactor()

        # check to make sure all response classes are represented, otherwise, quit
        if self.training_data[self.y_index].nlevels()[0] < self.class_number:
            print("Response classes are not represented in training dataset.")
            sys.exit(0)

        self.valid_data = h2o.import_file(pyunit_utils.locate(self.validation_data_file))
        self.valid_data[self.y_index] = self.valid_data[self.y_index].round().asfactor()
        self.test_data = h2o.import_file(pyunit_utils.locate(self.test_data_file))
        self.test_data[self.y_index] = self.test_data[self.y_index].round().asfactor()

        # make a bigger training set for grid search by combining data from validation data set
        self.training_data_grid = self.training_data.rbind(self.valid_data)

        # setup_data sklearn class weight of all ones.
        # Used only to make sure sklearn will know the correct number of classes
        for ind in range(self.class_number):
            self.sklearn_class_weight[ind] = 1.0

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

    def test1_glm_no_regularization(self):
        """
        S klearn logistic regression model is built.
        H2O GLM is built for Multinomial family with the same random data sets.  We observe
        the weights, confusion matrices from the two models.  We compare the logloss, prediction
        accuracy from the two models to determine if H2O GLM model shall pass the test.
        """

        print("*******************************************************************************************")
        print("Test1: build H2O GLM with Multinomial with no regularization.")
        h2o.cluster_info()

        # training result from python sklearn
        (p_weights, p_logloss_train, p_cm_train, p_accuracy_training, p_logloss_test, p_cm_test, p_accuracy_test) =\
            self.sklearn_multinomial_result(self.training_data_file, self.test_data_file, False, False)

        # get H2O model
        self.test_template_model = H2OGeneralizedLinearEstimator(family=self.family, Lambda=0)
        self.test_template_model.train(x=self.x_indices, y=self.y_index, training_frame=self.training_data)

        # calculate test metrics
        self.test_template_model_metrics = self.test_template_model.model_performance(test_data=self.test_data)

        num_test_failed = self.test_failed  # used to determine if current test has failed

        # print out comparison results for our H2O GLM with Sklearn model
        self.test_failed =\
            pyunit_utils.extract_comparison_attributes_and_print_multinomial(self.test_template_model,
                                                                             self.test_template_model_metrics,
                                                                             self.family, "\nTest1 Done!",
                                                                             compare_att_str=[
                                                                                 "\nComparing intercept and "
                                                                                 "weights ....",
                                                                                 "\nComparing logloss from training "
                                                                                 "data set ....",
                                                                                 "\nComparing logloss from test "
                                                                                 "data set ....",
                                                                                 "\nComparing confusion matrices from "
                                                                                 "training data set ....",
                                                                                 "\nComparing confusion matrices "
                                                                                 "from test data set ...",
                                                                                 "\nComparing accuracy from "
                                                                                 "training data set ....",
                                                                                 "\nComparing accuracy from test"
                                                                                 " data set ...."],
                                                                             h2o_att_str=[
                                                                                 "H2O intercept and weights: \n",
                                                                                 "H2O logloss from training data set: ",
                                                                                 "H2O logloss from test data set",
                                                                                 "H2O confusion matrix from training"
                                                                                 " data set: \n",
                                                                                 "H2O confusion matrix from test "
                                                                                 "data set: \n",
                                                                                 "H2O accuracy from training "
                                                                                 "data set: ",
                                                                                 "H2O accuracy from test data set: "],
                                                                             template_att_str=[
                                                                                 "Sklearn intercept and weights: \n",
                                                                                 "Sklearn logloss from training "
                                                                                 "data set: ",
                                                                                 "Sklearn logloss from test data set: ",
                                                                                 "Sklearn confusion matrix from "
                                                                                 "training data set: \n",
                                                                                 "Sklearn confusion matrix from test"
                                                                                 " data set: \n",
                                                                                 "Sklearn accuracy from training"
                                                                                 " data set: ",
                                                                                 "Sklearn accuracy from "
                                                                                 "test data set: "],
                                                                             att_str_fail=[
                                                                                 "Intercept and weights are not equal!",
                                                                                 "Logloss from training data set "
                                                                                 "differ too much!",
                                                                                 "Logloss from test data "
                                                                                 "set differ too much!",
                                                                                 "", "",
                                                                                 "Accuracies from "
                                                                                 "training data set differ"
                                                                                 " too much!",
                                                                                 "Accuracies from test "
                                                                                 "data set differ too much!"],
                                                                             att_str_success=[
                                                                                 "Intercept and weights are close"
                                                                                 " enough!",
                                                                                 "Logloss from training data set"
                                                                                 " are close enough!",
                                                                                 "Logloss from test data set are "
                                                                                 "close enough!", "", "",
                                                                                 "Accuracies from training data set "
                                                                                 "are close enough!",
                                                                                 "Accuracies from test data set are "
                                                                                 "close enough!"],
                                                                             can_be_better_than_template=[
                                                                                 True, True, True, True, True,
                                                                                 True, True],
                                                                             just_print=[True, True, True, True,
                                                                                         True, True, False],
                                                                             failed_test_number=self.test_failed,
                                                                             template_params=[
                                                                                 p_weights, p_logloss_train,
                                                                                 p_cm_train, p_accuracy_training,
                                                                                 p_logloss_test, p_cm_test,
                                                                                 p_accuracy_test],
                                                                             ignored_eps=self.ignored_eps,
                                                                             allowed_diff=self.allowed_diff)

        # print out test results and update test_failed_array status to reflect if this test has failed
        self.test_failed_array[self.test_num] += pyunit_utils.show_test_results("test1_glm_no_regularization",
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
        h2o.cluster_info()

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

        # print out comparison results for our H2O GLM
        self.test_failed =\
            pyunit_utils.extract_comparison_attributes_and_print_multinomial(model_h2o_0p5, h2o_model_0p5_test_metrics,
                                                                             self.family, "\nTest2 Done!",
                                                                             test_model=self.test_template_model,
                                                                             test_model_metric
                                                                             =self.test_template_model_metrics,
                                                                             compare_att_str=[
                                                                                 "\nComparing intercept and "
                                                                                 "weights ....",
                                                                                 "\nComparing logloss from training"
                                                                                 " data set ....",
                                                                                 "\nComparing logloss from test"
                                                                                 " data set ....",
                                                                                 "\nComparing confusion matrices "
                                                                                 "from training data set ....",
                                                                                 "\nComparing confusion matrices"
                                                                                 " from test data set ...",
                                                                                 "\nComparing accuracy from training"
                                                                                 " data set ....",
                                                                                 "\nComparing accuracy from test"
                                                                                 " data set ...."],
                                                                             h2o_att_str=[
                                                                                 "H2O lambda search intercept and"
                                                                                 " weights: \n",
                                                                                 "H2O lambda search logloss from "
                                                                                 "training data set: ",
                                                                                 "H2O lambda search logloss from test"
                                                                                 " data set",
                                                                                 "H2O lambda search confusion matrix"
                                                                                 " from training data set: \n",
                                                                                 "H2O lambda search confusion matrix"
                                                                                 " from test data set: \n",
                                                                                 "H2O lambda search accuracy from"
                                                                                 " training data set: ",
                                                                                 "H2O lambda search accuracy from"
                                                                                 " test data set: "],
                                                                             template_att_str=[
                                                                                 "H2O test1 template intercept "
                                                                                 "and weights: \n",
                                                                                 "H2O test1 template logloss from"
                                                                                 " training data set: ",
                                                                                 "H2O test1 template logloss from"
                                                                                 " test data set: ",
                                                                                 "H2O test1 template confusion "
                                                                                 "matrix from training data set: \n",
                                                                                 "H2O no regularization confusion "
                                                                                 "matrix from test data set: \n",
                                                                                 "H2O test1 template accuracy from"
                                                                                 " training data set: ",
                                                                                 "H2O test1 template accuracy from"
                                                                                 " test data set: "],
                                                                             att_str_fail=[
                                                                                 "Intercept and weights are not equal!",
                                                                                 "Logloss from training dataset differ"
                                                                                 " too much!",
                                                                                 "Logloss from test dataset differ"
                                                                                 " too much!", "", "",
                                                                                 "Accuracies from training dataset "
                                                                                 "differ too much!",
                                                                                 "Accuracies from test data set "
                                                                                 "differ too much!"],
                                                                             att_str_success=[
                                                                                 "Intercept and weights are close"
                                                                                 " enough!",
                                                                                 "Logloss from training data set are"
                                                                                 " close enough!",
                                                                                 "Logloss from test dataset are"
                                                                                 " close enough!", "", "",
                                                                                 "Accuracies from training data set"
                                                                                 " are close enough!",
                                                                                 "Accuracies from test data set are "
                                                                                 "close enough!"],
                                                                             can_be_better_than_template=[
                                                                                 True, True, True, True, True,
                                                                                 True, True],
                                                                             just_print=[
                                                                                 True, False, False, True, True,
                                                                                 False, False],
                                                                             failed_test_number=self.test_failed,
                                                                             ignored_eps=self.ignored_eps,
                                                                             allowed_diff=self.allowed_diff)

        # print out test results and update test_failed_array status to reflect if tests have failed
        self.test_failed_array[self.test_num] += pyunit_utils.show_test_results("test2_glm_lambda_search",
                                                                                num_test_failed, self.test_failed)
        self.test_num += 1

    def test3_glm_grid_search(self):
        """
        This test is used to test GridSearch with the following parameters:
        1. Lambda = best_lambda value from test2
        2. alpha = [0 0.5 0.99]
        3. cross-validation with nfolds = 5, fold_assignment = "Random"

        We will look at the best results from the grid search and compare it with H2O model built in test 1.

        :return: None
        """
        print("*******************************************************************************************")
        print("Test3: explores various parameter settings in training the GLM using GridSearch using solver ")
        h2o.cluster_info()

        hyper_parameters = {'alpha': [0, 0.5, 0.99]}  # set hyper_parameters for grid search

        # train H2O GLM model with grid search
        model_h2o_grid_search =\
            H2OGridSearch(H2OGeneralizedLinearEstimator(family=self.family, Lambda=self.best_lambda, nfolds=5,
                                                        fold_assignment='Random'), hyper_parameters)
        model_h2o_grid_search.train(x=self.x_indices, y=self.y_index, training_frame=self.training_data_grid)

        # print out the model sequence ordered by the best MSE values, thanks Ludi!
        temp_model = model_h2o_grid_search.sort_by("logloss(xval=True)")

        # obtain the model ID of best model (with smallest MSE) and use that for our evaluation
        best_model_id = temp_model['Model Id'][0]
        self.best_grid_logloss = temp_model['logloss(xval=True)'][0]
        self.best_alpha = model_h2o_grid_search.get_hyperparams(best_model_id)

        best_model = h2o.get_model(best_model_id)
        best_model_test_metrics = best_model.model_performance(test_data=self.test_data)

        num_test_failed = self.test_failed

        # print out comparison results for our H2O GLM
        self.test_failed =\
            pyunit_utils.extract_comparison_attributes_and_print_multinomial(best_model, best_model_test_metrics,
                                                                             self.family,
                                                                             "\nTest3 Done!",
                                                                             test_model=self.test_template_model,
                                                                             test_model_metric
                                                                             =self.test_template_model_metrics,
                                                                             compare_att_str=[
                                                                                 "\nComparing intercept and "
                                                                                 "weights ....",
                                                                                 "\nComparing logloss from "
                                                                                 "training data set ....",
                                                                                 "\nComparing logloss from "
                                                                                 "test data set ....",
                                                                                 "\nComparing confusion matrices"
                                                                                 " from training data set ....",
                                                                                 "\nComparing confusion matrices"
                                                                                 " from test data set ...",
                                                                                 "\nComparing accuracy from "
                                                                                 "training data set ....",
                                                                                 "\nComparing accuracy from test"
                                                                                 " data set ...."],
                                                                             h2o_att_str=[
                                                                                 "H2O grid search intercept and"
                                                                                 " weights: \n",
                                                                                 "H2O grid search logloss from "
                                                                                 "training data set: ",
                                                                                 "H2O grid search logloss from test "
                                                                                 "data set",
                                                                                 "H2O grid search confusion matrix "
                                                                                 "from training data set: \n",
                                                                                 "H2O grid search confusion matrix "
                                                                                 "from test data set: \n",
                                                                                 "H2O grid search accuracy from "
                                                                                 "training data set: ",
                                                                                 "H2O grid search accuracy from "
                                                                                 "test data set: "],
                                                                             template_att_str=[
                                                                                 "H2O test1 template intercept "
                                                                                 "and weights: \n",
                                                                                 "H2O test1 template logloss from"
                                                                                 " training data set: ",
                                                                                 "H2O test1 template logloss from"
                                                                                 " test data set: ",
                                                                                 "H2O test1 template confusion "
                                                                                 "matrix from training data set: \n",
                                                                                 "H2O test1 template confusion "
                                                                                 "matrix from test data set: \n",
                                                                                 "H2O test1 template accuracy "
                                                                                 "from training data set: ",
                                                                                 "H2O test1 template accuracy from"
                                                                                 " test data set: "],
                                                                             att_str_fail=[
                                                                                 "Intercept and weights are not equal!",
                                                                                 "Logloss from training data set "
                                                                                 "differ too much!",
                                                                                 "Logloss from test dataset differ "
                                                                                 "too much!", "", "",
                                                                                 "Accuracies from training data set "
                                                                                 "differ too much!",
                                                                                 "Accuracies from test data set "
                                                                                 "differ too much!"],
                                                                             att_str_success=[
                                                                                 "Intercept and weights are close"
                                                                                 " enough!",
                                                                                 "Logloss from training data set are"
                                                                                 " close enough!",
                                                                                 "Logloss from test data set are "
                                                                                 "close enough!", "", "",
                                                                                 "Accuracies from training data set "
                                                                                 "are close enough!",
                                                                                 "Accuracies from test data set are "
                                                                                 "close enough!"],
                                                                             can_be_better_than_template=[
                                                                                 True, True, True, True, True,
                                                                                 True, True],
                                                                             just_print=[
                                                                                 True, True, True, True, True,
                                                                                 True, False],
                                                                             failed_test_number=self.test_failed,
                                                                             ignored_eps=self.ignored_eps,
                                                                             allowed_diff=self.allowed_diff)

        # print out test results and update test_failed_array status to reflect if this test has failed
        self.test_failed_array[self.test_num] += pyunit_utils.show_test_results("test_glm_grid_search_over_params",
                                                                                num_test_failed, self.test_failed)
        self.test_num += 1

    def test5_missing_values(self):
        """
        Test parameter missing_values_handling="MeanImputation" with
        only real value predictors.  The same data sets as before are used.  However, we
        go into the predictor matrix and randomly decide to change a value to be
        nan and create missing values.  Since no regularization is enabled in this case,
        we are able to build a Sklearn model where we can
        compare our H2O models with.
        """
        print("*******************************************************************************************")
        print("Test5: test the GLM with imputation of missing values with column averages.")
        h2o.cluster_info()

        # training result from python sklearn
        (p_weights, p_logloss_train, p_cm_train, p_accuracy_training, p_logloss_test, p_cm_test, p_accuracy_test) =\
            self.sklearn_multinomial_result(self.training_data_file_nans, self.test_data_file_nans, False, False)

        # import training set and test set
        training_data = h2o.import_file(pyunit_utils.locate(self.training_data_file_nans))
        test_data = h2o.import_file(pyunit_utils.locate(self.test_data_file_nans))

        # change the response columns to be categorical
        training_data[self.y_index] = training_data[self.y_index].round().asfactor()
        test_data[self.y_index] = test_data[self.y_index].round().asfactor()

        # train H2O models with missing_values_handling="MeanImputation"
        model_h2o = H2OGeneralizedLinearEstimator(family=self.family, Lambda=0,
                                                  missing_values_handling="MeanImputation")
        model_h2o.train(x=self.x_indices, y=self.y_index, training_frame=training_data)

        # calculate H2O model performance with test data set
        h2o_model_test_metrics = model_h2o.model_performance(test_data=test_data)

        num_test_failed = self.test_failed

        # print out comparison results for our H2O GLM
        self.test_failed =\
            pyunit_utils.extract_comparison_attributes_and_print_multinomial(model_h2o, h2o_model_test_metrics,
                                                                             self.family, "\nTest5 Done!",
                                                                             compare_att_str=[
                                                                                 "\nComparing intercept and "
                                                                                 "weights ....",
                                                                                 "\nComparing logloss from "
                                                                                 "training data set ....",
                                                                                 "\nComparing logloss from test"
                                                                                 " data set ....",
                                                                                 "\nComparing confusion matrices from"
                                                                                 " training data set ....",
                                                                                 "\nComparing confusion matrices from"
                                                                                 " test data set ...",
                                                                                 "\nComparing accuracy from training"
                                                                                 " data set ....",
                                                                                 "\nComparing accuracy from test "
                                                                                 "data set ...."],
                                                                             h2o_att_str=[
                                                                                 "H2O missing values intercept and"
                                                                                 " weights: \n",
                                                                                 "H2O missing values logloss from "
                                                                                 "training data set: ",
                                                                                 "H2O missing values logloss from "
                                                                                 "test data set",
                                                                                 "H2O missing values confusion matrix"
                                                                                 " from training data set: \n",
                                                                                 "H2O missing values confusion matrix"
                                                                                 " from test data set: \n",
                                                                                 "H2O missing values accuracy from"
                                                                                 " training data set: ",
                                                                                 "H2O missing values accuracy from "
                                                                                 "test data set: "],
                                                                             template_att_str=[
                                                                                 "Sklearn missing values intercept "
                                                                                 "and weights: \n",
                                                                                 "Sklearn missing values logloss from"
                                                                                 " training data set: ",
                                                                                 "Sklearn missing values logloss from"
                                                                                 " test data set: ",
                                                                                 "Sklearn missing values confusion "
                                                                                 "matrix from training data set: \n",
                                                                                 "Sklearn missing values confusion "
                                                                                 "matrix from test data set: \n",
                                                                                 "Sklearn missing values accuracy "
                                                                                 "from training data set: ",
                                                                                 "Sklearn missing values accuracy "
                                                                                 "from test data set: "],
                                                                             att_str_fail=[
                                                                                 "Intercept and weights are not equal!",
                                                                                 "Logloss from training data set "
                                                                                 "differ too much!",
                                                                                 "Logloss from test data set differ "
                                                                                 "too much!", "", "",
                                                                                 "Accuracies from training data set "
                                                                                 "differ too much!",
                                                                                 "Accuracies from test data set "
                                                                                 "differ too much!"],
                                                                             att_str_success=[
                                                                                 "Intercept and weights are close"
                                                                                 " enough!",
                                                                                 "Logloss from training data set are"
                                                                                 " close enough!",
                                                                                 "Logloss from test data set are "
                                                                                 "close enough!", "", "",
                                                                                 "Accuracies from training data set "
                                                                                 "are close enough!",
                                                                                 "Accuracies from test data set are"
                                                                                 " close enough!"],
                                                                             can_be_better_than_template=[
                                                                                 True, True, True, True, True,
                                                                                 True, True],
                                                                             just_print=[
                                                                                 True, True, True, True, True,
                                                                                 True, False],
                                                                             failed_test_number=self.test_failed,
                                                                             template_params =[
                                                                                 p_weights, p_logloss_train, p_cm_train,
                                                                                 p_accuracy_training, p_logloss_test,
                                                                                 p_cm_test,p_accuracy_test],
                                                                             ignored_eps=self.ignored_eps,
                                                                             allowed_diff=self.allowed_diff)

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
        as before.  Next, we go into the predictor matrix and randomly
        decide to change a value to be nan and create missing values.  Since no regularization
        is enabled in this case, we are able to build Sklearn model
        where we can compare our H2O models with.
        """

        # no regularization in this case, use reference level plus one-hot-encoding
        print("*******************************************************************************************")
        print("Test6: test the GLM with enum/real values.")
        h2o.cluster_info()

        # training result from python sklearn
        (p_weights, p_logloss_train, p_cm_train, p_accuracy_training, p_logloss_test, p_cm_test, p_accuracy_test) =\
            self.sklearn_multinomial_result(self.training_data_file_enum_nans, self.test_data_file_enum_nans,
                                            True, False)

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

        # change response variables to be categorical
        training_data[y_index] = training_data[y_index].round().asfactor()

        # check to make sure all response classes are represented, otherwise, quit
        if training_data[y_index].nlevels()[0] < self.class_number:
            print("Response classes are not represented in training dataset.")
            sys.exit(0)

        test_data[y_index] = test_data[y_index].round().asfactor()

        # generate H2O model
        model_h2o = H2OGeneralizedLinearEstimator(family=self.family, Lambda=0,
                                                  missing_values_handling="MeanImputation")
        model_h2o.train(x=x_indices, y=y_index, training_frame=training_data)

        h2o_model_test_metrics = model_h2o.model_performance(test_data=test_data)

        num_test_failed = self.test_failed

        # print out comparison results for weight/p-values/training and test MSEs for theoretical and our H2O GLM
        self.test_failed =\
            pyunit_utils.extract_comparison_attributes_and_print_multinomial(model_h2o, h2o_model_test_metrics,
                                                                             self.family, "\nTest6 Done!",
                                                                             compare_att_str=[
                                                                                 "\nComparing intercept and "
                                                                                 "weights ....",
                                                                                 "\nComparing logloss from training "
                                                                                 "data set ....",
                                                                                 "\nComparing logloss from test data"
                                                                                 " set ....",
                                                                                 "\nComparing confusion matrices from"
                                                                                 " training data set ....",
                                                                                 "\nComparing confusion matrices "
                                                                                 "from test data set ...",
                                                                                 "\nComparing accuracy from training"
                                                                                 " data set ....",
                                                                                 "\nComparing accuracy from test data"
                                                                                 " set ...."],
                                                                             h2o_att_str=[
                                                                                 "H2O with enum/real values, no "
                                                                                 "regularization and missing values "
                                                                                 "intercept and weights: \n",
                                                                                 "H2O with enum/real values, no "
                                                                                 "regularization and missing values "
                                                                                 "logloss from training data set: ",
                                                                                 "H2O with enum/real values, no "
                                                                                 "regularization and missing values "
                                                                                 "logloss from test data set",
                                                                                 "H2O with enum/real values, no "
                                                                                 "regularization and missing values "
                                                                                 "confusion matrix from training data "
                                                                                 "set: \n",
                                                                                 "H2O with enum/real values, no "
                                                                                 "regularization and missing values "
                                                                                 "confusion matrix from test "
                                                                                 "data set: \n",
                                                                                 "H2O with enum/real values, no "
                                                                                 "regularization and missing values "
                                                                                 "accuracy from training data set: ",
                                                                                 "H2O with enum/real values, no "
                                                                                 "regularization and missing values "
                                                                                 "accuracy from test data set: "],
                                                                             template_att_str=[
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values"
                                                                                 " intercept and weights: \n",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values "
                                                                                 "logloss from training data set: ",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values "
                                                                                 "logloss from test data set: ",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values "
                                                                                 "confusion matrix from training data"
                                                                                 "set: \n",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values "
                                                                                 "confusion matrix from test data"
                                                                                 "set: \n",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values "
                                                                                 "accuracy from training data set: ",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "and missing values "
                                                                                 "accuracy from test data set: "],
                                                                             att_str_fail=[
                                                                                 "Intercept and weights are not equal!",
                                                                                 "Logloss from training data set differ"
                                                                                 " too much!",
                                                                                 "Logloss from test dataset differ "
                                                                                 "too much!", "", "",
                                                                                 "Accuracies from training data set "
                                                                                 "differ too much!",
                                                                                 "Accuracies from test data set "
                                                                                 "differ too much!"],
                                                                             att_str_success=[
                                                                                 "Intercept and weights are close "
                                                                                 "enough!",
                                                                                 "Logloss from training dataset are "
                                                                                 "close enough!",
                                                                                 "Logloss from test dataset are "
                                                                                 "close enough!", "", "",
                                                                                 "Accuracies from training data set "
                                                                                 "are close enough!",
                                                                                 "Accuracies from test data set are "
                                                                                 "close enough!"],
                                                                             can_be_better_than_template=[
                                                                                 True, True, True, True, True,
                                                                                 True, True],
                                                                             just_print=[
                                                                                 True, True, True, True, True,
                                                                                 True, False],
                                                                             failed_test_number=self.test_failed,
                                                                             template_params=[
                                                                                 p_weights, p_logloss_train, p_cm_train,
                                                                                 p_accuracy_training, p_logloss_test,
                                                                                 p_cm_test, p_accuracy_test],
                                                                             ignored_eps=self.ignored_eps,
                                                                             allowed_diff=self.allowed_diff)

        h2o.cluster_info()
        # print out test results and update test_failed_array status to reflect if this test has failed
        self.test_failed_array[self.test_num] += pyunit_utils.show_test_results("test6_enum_missing_values",
                                                                                num_test_failed, self.test_failed)
        self.test_num += 1

    def test7_missing_enum_values_lambda_search(self):
        """
        Test parameter missing_values_handling="MeanImputation" with mixed predictors (categorical/real value columns).
        We first generate a data set that
        contains a random number of columns of categorical and real value columns.  Next, we
        encode the categorical columns.  Then, we generate the random data set using the formula
        as before.  Next, we go into the predictor matrix and randomly
        decide to change a value to be nan and create missing values.  Lambda-search will be
        enabled with alpha set to 0.5.  Since the encoding is different in this case
        than in test6, we build a Sklearn model and compare the best H2O
        model MSEs with theoretical calculations and hope that they are close.
        """

        # perform lambda_search, regularization and one hot encoding.
        print("*******************************************************************************************")
        print("Test7: test the GLM with imputation of missing enum/real values under lambda search.")
        h2o.cluster_info()

        # training result from python sklearn
        (p_weights, p_logloss_train, p_cm_train, p_accuracy_training, p_logloss_test, p_cm_test, p_accuracy_test) =\
            self.sklearn_multinomial_result(self.training_data_file_enum_nans_true_one_hot,
                                            self.test_data_file_enum_nans_true_one_hot, True, True,
                                            validation_data_file=self.validation_data_file_enum_nans_true_one_hot)

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

        # change response column to be categorical
        training_data[y_index] = training_data[y_index].round().asfactor()

        # check to make sure all response classes are represented, otherwise, quit
        if training_data[y_index].nlevels()[0] < self.class_number:
            print("Response classes are not represented in training dataset.")
            sys.exit(0)

        validation_data[y_index] = validation_data[y_index].round().asfactor()
        test_data[y_index] = test_data[y_index].round().asfactor()

        # train H2O model
        model_h2o_0p5 = H2OGeneralizedLinearEstimator(family=self.family, lambda_search=True, alpha=0.5,
                                                      lambda_min_ratio=1e-20,
                                                      missing_values_handling="MeanImputation")
        model_h2o_0p5.train(x=x_indices, y=y_index, training_frame=training_data, validation_frame=validation_data)

        h2o_model_0p5_test_metrics = model_h2o_0p5.model_performance(test_data=test_data)

        num_test_failed = self.test_failed

        # print out comparison results for our H2O GLM
        self.test_failed =\
            pyunit_utils.extract_comparison_attributes_and_print_multinomial(model_h2o_0p5, h2o_model_0p5_test_metrics,
                                                                             self.family, "\nTest7 Done!",
                                                                             compare_att_str=[
                                                                                 "\nComparing intercept and "
                                                                                 "weights ....",
                                                                                 "\nComparing logloss from "
                                                                                 "training data set ....",
                                                                                 "\nComparing logloss from test "
                                                                                 "data set ....",
                                                                                 "\nComparing confusion matrices from"
                                                                                 "training data set ....",
                                                                                 "\nComparing confusion matrices from"
                                                                                 "test data set ...",
                                                                                 "\nComparing accuracy from training"
                                                                                 " data set ....",
                                                                                 "\nComparing accuracy from test "
                                                                                 "data set ...."],
                                                                             h2o_att_str=[
                                                                                 "H2O with enum/real values, "
                                                                                 "lamba search and missing values"
                                                                                 " intercept and weights: \n",
                                                                                 "H2O with enum/real values, lamba"
                                                                                 " search and missing values "
                                                                                 "logloss from training data set: ",
                                                                                 "H2O with enum/real values, lamba"
                                                                                 " search and missing values "
                                                                                 "logloss from test data set",
                                                                                 "H2O with enum/real values, "
                                                                                 "lamba search and missing values"
                                                                                 " confusion matrix from training"
                                                                                 " data set: \n",
                                                                                 "H2O with enum/real values, "
                                                                                 "lamba search and missing values"
                                                                                 " confusion matrix from test "
                                                                                 "data set: \n",
                                                                                 "H2O with enum/real values, "
                                                                                 "lamba search and missing values"
                                                                                 " accuracy from training "
                                                                                 "data set: ",
                                                                                 "H2O with enum/real values,"
                                                                                 " lamba search and missing values"
                                                                                 " accuracy from test data set: "],
                                                                             template_att_str=[
                                                                                 "Sklearn with enum/real values,"
                                                                                 " missing values"
                                                                                 " intercept and weights: \n",
                                                                                 "Sklearn with enum/real values,"
                                                                                 " missing values"
                                                                                 " logloss from training "
                                                                                 "data set: ",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values"
                                                                                 " logloss from test data set: ",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values"
                                                                                 " confusion matrix from "
                                                                                 "training data set: \n",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values"
                                                                                 " confusion matrix from test"
                                                                                 " data set: \n",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values"
                                                                                 " accuracy from training"
                                                                                 " data set: ",
                                                                                 "Sklearn with enum/real values, "
                                                                                 "missing values"
                                                                                 " accuracy from test data set: "],
                                                                             att_str_fail=[
                                                                                 "Intercept and weights are not"
                                                                                 " equal!",
                                                                                 "Logloss from training data "
                                                                                 "set differ too much!",
                                                                                 "Logloss from test data set "
                                                                                 "differ too much!", "", "",
                                                                                 "Accuracies from training data"
                                                                                 " set differ too much!",
                                                                                 "Accuracies from test data set "
                                                                                 "differ too much!"],
                                                                             att_str_success=[
                                                                                 "Intercept and weights are close"
                                                                                 " enough!",
                                                                                 "Logloss from training data set"
                                                                                 " are close enough!",
                                                                                 "Logloss from test data set are"
                                                                                 " close enough!", "", "",
                                                                                 "Accuracies from training data"
                                                                                 " set are close enough!",
                                                                                 "Accuracies from test data set "
                                                                                 "are close enough!"],
                                                                             can_be_better_than_template=[
                                                                                 True, True, True, True,
                                                                                 True, True, True],
                                                                             just_print=[
                                                                                 True, True, True, True,
                                                                                 True, True, False],
                                                                             failed_test_number=self.test_failed,
                                                                             template_params=[
                                                                                 p_weights, p_logloss_train,
                                                                                 p_cm_train, p_accuracy_training,
                                                                                 p_logloss_test, p_cm_test,
                                                                                 p_accuracy_test],
                                                                             ignored_eps=self.ignored_eps,
                                                                             allowed_diff=self.allowed_diff)

        # print out test results and update test_failed_array status to reflect if tests have failed
        self.test_failed_array[self.test_num] +=\
            pyunit_utils.show_test_results("test7_missing_enum_values_lambda_search",
                                           num_test_failed, self.test_failed)
        self.test_num += 1

    def sklearn_multinomial_result(self, training_data_file, test_data_file, has_categorical, true_one_hot,
                                   validation_data_file=""):
        """
        This function will generate a Sklearn multinomial model using the same set of data sets we have used to build
        our H2O models.  The purpose here is to be able to compare the performance of H2O
        models with the Sklearn model built here.  This is useful in cases where theoretical solutions
        do not exist.  If the data contains missing values, mean imputation is applied to the data set before
        a Sklearn model is built.  In addition, if there are enum columns in predictors and also missing values,
        the same encoding and missing value imputation method used by H2O is applied to the data set before we build
        the Sklearn model.

        :param training_data_file: string storing training data set filename with directory path.
        :param test_data_file: string storing test data set filename with directory path.
        :param has_categorical: bool indicating if the data set contains mixed predictors (both enum and real)
        :param true_one_hot: bool True: true one hot encoding is used.  False: reference level plus one hot encoding
         is used
        :param validation_data_file: optional string, denoting validation file so that we can concatenate
         training and validation data sets into a big training set since H2O model is using a training
         and a validation data set.

        :return: a tuple containing the weights, logloss, confusion matrix, prediction accuracy calculated on training
        data set and test data set respectively.
        """

        # read in the training data
        training_data_xy = np.asmatrix(np.genfromtxt(training_data_file, delimiter=',', dtype=None))
        test_data_xy = np.asmatrix(np.genfromtxt(test_data_file, delimiter=',', dtype=None))

        if len(validation_data_file) > 0:    # validation data set exist and add it to training_data
            temp_data_xy = np.asmatrix(np.genfromtxt(validation_data_file, delimiter=',', dtype=None))
            training_data_xy = np.concatenate((training_data_xy, temp_data_xy), axis=0)

        # if predictor contains categorical data, perform encoding of enums to binary bits
        # for missing categorical enums, a new level is created for the nans
        if has_categorical:
            training_data_xy = pyunit_utils.encode_enum_dataset(training_data_xy, self.enum_level_vec,
                                                                self.enum_col, true_one_hot,
                                                                np.any(training_data_xy))
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

        # now data is ready to be massaged  into format that sklearn can use
        (response_y, x_mat) = pyunit_utils.prepare_data_sklearn_multinomial(training_data_xy)
        (t_response_y, t_x_mat) = pyunit_utils.prepare_data_sklearn_multinomial(test_data_xy)

        # train the Sklearn model
        sklearn_model = LogisticRegression(multi_class='multinomial', solver='lbfgs',
                                           class_weight=self.sklearn_class_weight)
        sklearn_model = sklearn_model.fit(x_mat, response_y)

        # grab the training performance metrics
        accuracy_training = sklearn_model.score(x_mat, response_y)
        weights = sklearn_model.coef_
        p_response_y = sklearn_model.predict(x_mat)
        log_prob = sklearn_model.predict_log_proba(x_mat)
        logloss_training = self.logloss_sklearn(response_y, log_prob)
        cm_train = metrics.confusion_matrix(response_y, p_response_y)

        # grab the testing performance metrics
        p_response_y = sklearn_model.predict(t_x_mat)
        log_prob = sklearn_model.predict_log_proba(t_x_mat)
        logloss_test = self.logloss_sklearn(t_response_y, log_prob)
        cm_test = metrics.confusion_matrix(t_response_y, p_response_y)
        accuracy_test = metrics.accuracy_score(t_response_y, p_response_y)

        return weights, logloss_training, cm_train,accuracy_training, logloss_test, cm_test, accuracy_test

    def logloss_sklearn(self, true_y, log_prob):
        """
        This function calculate the mean logloss given the true response class in true_y and the log
        prob(y=k|X,W) for all classes.

        :param true_y: vector denoting true response class
        :param log_prob: matrix of log of prob(y=k|X, W) for all classes

        :return: mean logloss
        """
        (num_row, num_class) = log_prob.shape

        logloss = 0.0
        for ind in range(num_row):
            logloss += log_prob[ind, int(true_y[ind])]

        return -1.0 * logloss / num_row


def test_glm_multinomial():
    """
    Create and instantiate TestGLMMultinomial class and perform tests specified for the GLM
    Multinomial family.

    :return: None
    """
    test_glm_multinomial = TestGLMMultinomial()
    test_glm_multinomial.test1_glm_no_regularization()
    test_glm_multinomial.test2_glm_lambda_search()
    test_glm_multinomial.test3_glm_grid_search()
    test_glm_multinomial.test_num += 1      # increment the test number to reuse tear_down()
    test_glm_multinomial.test5_missing_values()
    test_glm_multinomial.test6_enum_missing_values()
    test_glm_multinomial.test7_missing_enum_values_lambda_search()
    test_glm_multinomial.teardown()

    sys.stdout.flush()

    if test_glm_multinomial.test_failed:  # exit with error if any tests have failed
        sys.exit(1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_multinomial)
else:
    test_glm_multinomial()
