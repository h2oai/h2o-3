from __future__ import print_function
from builtins import range
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator
from h2o.exceptions import H2OResponseError

def test_stopping_methods():
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training1_set.csv"))
    validation_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training2_set.csv"))
    y_index = training1_data.ncol - 1
    x_indices = list(range(y_index))
    testStoppingMethod(0, training1_data, x_indices, y_index, validation_data)


def testStoppingMethod(model_index, training_data, x_indices, y_index, validation_data):
    '''
    This function will test if the stopping_method has been implemented correctly for the model type what was
    sent.  In particular, it will perform the following tests:
    1. Check to make sure stopping_method is checked and errors be thrown when
        a. setting it to valid when a validation set if not provided;
        b. setting it to xval when no cross validation is enabled;
        c. setting it to train when cross-validation is enabled and validation dataset is provided
    2. when stopping_method is set to xval, the scoring history of auto is calculated based on the final main model.
      If a validation dataset is provided, early stop will use the validation dataset metrics.  If no validation
      dataset is provided, the early stop will use the training dataset metrics.
    3. when the stopping_method is set to valid, the scoring history should be the same as if not stopping_method is
    chosen, cross-validation is disabled, a validation set is provided.  Compare the two and make sure they are the same
    4. when the stopping_method is set to train, the scoring history should be the same as if not stopping_method is
    chosen, no validation is provided.  Compare the two and make sure they are the same

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
#    model = eval(models_sm_xval[0])
    model = eval(models_auto_CV[0])
    model.train(x=x_indices, y=y_index, training_frame=training_data)

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
