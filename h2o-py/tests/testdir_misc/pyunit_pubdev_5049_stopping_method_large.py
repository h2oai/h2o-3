from __future__ import print_function
from builtins import range
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.xgboost import *
from h2o.exceptions import H2OResponseError

'''
    This unit test will test if the stopping_method has been implemented correctly for deeplearning, GBM, DRF and
    XGboost models. There are four stopping methods: AUTO (default behavior), train, xval and valid.  This is how
    I implemented it:

    1. If you only have training data and no cross-validation, the valid stopping methods are AUTO and train.
    2. If you only have training data and with cross-validation enabled, the valid stopping methods are AUTO, train
       and xval.
    3. If you have training data, no cross-validation and a validation data, the valid stopping methods are AUTO,
       train and valid.
    4. If you have training data, cross-validation enabled and a validation data, the valid stopping methods are AUTO,
       train, xval and valid.  In this case, the scoring for cross-validation is stored in score_train and scoring for
       validation data is stored in score_valid in Java backend.  Hence, in this case, choosing train and xval will 
       use the same metric stored in score_train.

    First test set is for negative tests to make sure that correct stopping_methods are used.
    
    Second test set is to make sure that the correct metric is used for early stop for each stopping_method.  To ensure
    that this is the case, I will compare the scoring_history and the stopping_method metrics used with when the
    stopping_method is set to be AUTO.  They should all agree.
'''
def test_stopping_methods():
    assert H2OXGBoostEstimator.available() is True
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training1_set.csv"))
    validation_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training2_set.csv"))
    y_index = training1_data.ncol - 1
    x_indices = list(range(y_index))

    # Test set 1, negative tests
    negativeTests = [
        'H2OGradientBoostingEstimator(stopping_rounds=3, stopping_tolerance=0.01, stopping_method="valid")', # training set only but stopping_method=valid
        "H2ODeepLearningEstimator(hidden=[3], stopping_method='xval', stopping_rounds=3, stopping_tolerance=0.01)", # training set only but stopping_method=xval
        'H2OXGBoostEstimator(nfolds=3, stopping_method="valid")', # training set with cross-validation enabled but stopping_method=valid
        'H2ORandomForestEstimator(ntrees=10, stopping_method="xval")']
    validation_data_set = [False, False, False, True, True]


    for index in range(len(negativeTests)):
        negativeTestStoppingMethod(negativeTests, training1_data, x_indices, y_index, validation_data,
                                   validation_data_set, index)

    # Test set 2, test stopping methods
    stopping_method_tests = [
        'H2OGradientBoostingEstimator(stopping_rounds=3, stopping_tolerance=0.01, stopping_method="train", seed=12345)', # stopping method train
        "H2ODeepLearningEstimator(hidden=[3], stopping_method='xval', stopping_rounds=3, stopping_tolerance=0.01, nfolds=3, seed=12345, reproducible=True)", # stopping method xval
        'H2OXGBoostEstimator(nfolds=3, stopping_method="valid", seed=12345)', # stopping method valid
        'H2ORandomForestEstimator(ntrees=10, stopping_method="xval", nfolds=3, seed=12345)'] # training set with validation set only but stopping_method=xval
    equivalent_auto_tests = [
        'H2OGradientBoostingEstimator(stopping_rounds=3, stopping_tolerance=0.01, seed=12345)', # stopping method train
        "H2ODeepLearningEstimator(hidden=[3],  stopping_rounds=3, stopping_tolerance=0.01, nfolds=3, seed=12345, reproducible=True)", # stopping method xval
        'H2OXGBoostEstimator(stopping_method="valid", seed=12345)', # stopping method valid
        'H2ORandomForestEstimator(ntrees=10, nfolds=3, seed=12345)'] # training set with validation set only but stopping_method=xval

    validation_data_set = [False, False, True, True]
    validation_data_auto = [False, False, True, False]

    for index in range(len(stopping_method_tests)):
        testStoppingMethod(stopping_method_tests, training1_data, x_indices, y_index, validation_data,
                           validation_data_set, index, equivalent_auto_tests, validation_data_auto)

def testStoppingMethod(testString, training_data, x_indices, y_index, validation_data, use_valid_set, test_index,
                       equivalent_auto_tests, use_auto_valid_set):
    print("testing....{0}".format(testString[test_index]))
    model = eval(testString[test_index])
    model_auto = eval(equivalent_auto_tests[test_index])

    model_auto = trainModel(model_auto, use_auto_valid_set[test_index], training_data, x_indices, y_index, validation_data)
    model = trainModel(model, use_valid_set[test_index], training_data, x_indices, y_index, validation_data)

    # model and model_auto should have equivalent scoring history as early stopped on them are evaulated using the
    # same metrics.  However, this one capture only the training metrics
    col_header_list = ["training_rmse", "training_deviance", "training_mae"]
    for cnames in col_header_list:
        c1 = pyunit_utils.extract_scoring_history_field(model, cnames)
        c2 = pyunit_utils.extract_scoring_history_field(model_auto, cnames)
        assert pyunit_utils.equal_two_arrays(c1, c2, 1e-6, 1e-6, False), "Scoring history for {2} expected {0} but received {1}".format(c1, c2, cnames)

    # in addition, the two models should have equivalent metrics depending on the stopping method.  Check that.
    if model._parms["stopping_method"] == 'xval':   # check the cross-validation metrics here
        assert_stopping_method_metrics_equal(model, model_auto, "cross_validation_metrics_summary")
    if model._parms["stopping_method"] == 'valid':  # check the validation metrics here
        metrics_compare = ["MSE", "RMSE","r2", "mae"]
        for m in metrics_compare:
            vmodel = model._model_json["output"]["validation_metrics"]._metric_json[m]
            vmodel_auto = model_auto._model_json["output"]["validation_metrics"]._metric_json[m]
            assert abs(vmodel-vmodel_auto) < 1e-10, "validation metric {0} with stopping_method is {1} and " \
                                                         "differs from AUTO value of {2}".format(m, vmodel, vmodel_auto)

def assert_stopping_method_metrics_equal(model, model_auto, tableName):
    col_header = model._model_json["output"][tableName]._col_header
    del col_header[0] # remove empty list
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(model._model_json["output"][tableName],
                                                  model_auto._model_json["output"][tableName], col_header,
                                                  tolerance=1e-10)

def trainModel(a_model, use_valid, training_data, x_indices, y_index, validation_data):
    if use_valid:
        a_model.train(x=x_indices, y=y_index, training_frame=training_data, validation_frame=validation_data)
    else:
        a_model.train(x=x_indices, y=y_index, training_frame=training_data)
    return a_model

def negativeTestStoppingMethod(modelString, training_data, x_indices, y_index, validation_data, use_valid_set, test_index):
    print("Model setup is: {0}".format(modelString[test_index]))

    try:
        model = eval(modelString[test_index])
        if use_valid_set[test_index]:
            model.train(x=x_indices, y=y_index, training_frame=training_data, validation_frame=validation_data)
        else:
            model.train(x=x_indices, y=y_index, training_frame=training_data)
        sys.exit(1) # should not be here.  An exception should have been thrown
    except Exception as exerr:
        print("**** test failed correctly with error {0}".format(exerr))

def testStoppingMethods(model_index, training_data, x_indices, y_index, validation_data):
    '''

    :param model_index: choose model to be tested, 0 for deeplearning, 1 for GBM or 2 for RF
    :param training_data: training data frame
    :param x_indices: predictor column lists
    :param y_index: response column
    :param validation_data: validation dataset
    :return: pass or fail the test
    '''
    models_sm_valid_bad = ["H2ODeepLearningEstimator(distribution='gaussian', seed=12345, hidden=[3], "
                           "stopping_method='valid', stopping_rounds=3, stopping_tolerance=0.01, nfolds=3)"]
    models_sm_xval_bad = ["H2ODeepLearningEstimator(distribution='gaussian', seed=12345, hidden=[3], "
                          "stopping_method='xval', stopping_rounds=3, stopping_tolerance=0.01)"]
    models_gbm = ['H2OGradientBoostingEstimator(seed=12345, stopping_rounds=3, stopping_tolerance=0.01)']

    models_sm_xval = [
        "H2ODeepLearningEstimator(distribution='gaussian', seed=12345, hidden=[3], stopping_method='xval', "
        "stopping_rounds=3, stopping_tolerance=0.01, nfolds=3)"]
    models_sm_valid = [
        "H2ODeepLearningEstimator(distribution='gaussian', seed=12345, hidden=[3], stopping_method='valid',"
        " stopping_rounds=3, stopping_tolerance=0.01)"]
    models_sm_train = [
        "H2ODeepLearningEstimator(distribution='gaussian', seed=12345, hidden=[3], stopping_method='train',"
        " stopping_rounds=3, stopping_tolerance=0.01)"]

    models_auto_CV = [
        "H2ODeepLearningEstimator(distribution='gaussian', seed=12345, hidden=[3], stopping_rounds=3,"
        " stopping_tolerance=0.01, nfolds=3)"]
    models_auto_noCV = [
        "H2ODeepLearningEstimator(distribution='gaussian', seed=12345, hidden=[3], stopping_rounds=3,"
        " stopping_tolerance=0.01)"]


# play with it first
    # everything, training dataset, cv and validation dataset
    model = eval(models_auto_CV[0])
#    model = eval(models_gbm[0])
    model.train(x=x_indices, y=y_index, training_frame=training_data, validation_frame=validation_data)

    # test 1, set stopping method to valid without a validation set
    try:
        model = eval(models_sm_valid_bad[model_index])
        model.train(x=x_indices, y=y_index, training_frame=training_data)
    except H2OResponseError:
        print("Exception expected and thrown: setting stopping method to valid without a validation frame.")

    # test 2, set stopping method to xval without enabling cross-validation
    try:
        model = eval(models_sm_xval_bad[model_index])
        model.train(x=x_indices, y=y_index, training_frame=training_data, validation_frame=validation_data)
    except H2OResponseError:
        print("Exception expected and thrown: setting stopping method to xval without enabling cross-validation.")

    # test 5, compare scoring history of auto without cross-validation and validation datset, stopping method set to train
    col_header_list = ["training_rmse", "training_deviance", "training_mae", "training_r2"]
    compareBothModels(models_auto_noCV[model_index], models_sm_train[model_index], training_data, validation_data,
                      None, x_indices, y_index, col_header_list)

    # test 3, compare scoring history of auto with cross-validation and stopping method to xval

    compareBothModels(models_auto_CV[model_index], models_sm_xval[model_index], training_data, validation_data,
                      None, x_indices, y_index, col_header_list)

    # test 4, compare scoring history of auto without cross-valiation but with validation dataset and stopping method set to valid
    col_header_list = ["validation_rmse", "validation_deviance", "validation_mae", "validation_r2"]
    compareBothModels(models_auto_noCV[model_index], models_sm_valid[model_index], training_data, validation_data,
                      validation_data, x_indices, y_index, col_header_list)





def compareBothModels(modelAutoStr, modelSMStr, training_data, validation_data, validation_data_auto,
                      x_indices, y_index, col_header_list):
    model = eval(modelSMStr)
    model.train(x=x_indices, y=y_index, training_frame=training_data, validation_frame=validation_data)

    model_auto=eval(modelAutoStr)
    model_auto.train(x=x_indices, y=y_index, training_frame=training_data, validation_frame=validation_data_auto)

    for cnames in col_header_list:
        c1 = pyunit_utils.extract_scoring_history_field(model, cnames)
        c2 = pyunit_utils.extract_scoring_history_field(model_auto, cnames)
       # assert pyunit_utils.equal_two_arrays(c1, c2, 1e-6, 1e-6, False), "Scoring history for {2} expected {0} but received {1}".format(c1, c2, cnames)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_stopping_methods)
else:
    test_stopping_methods()
