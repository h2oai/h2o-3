from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

'''
    This function will test if the stopping_method has been implemented correctly for the model type what was
    sent.  In particular, it will perform the following tests:
    1. Check to make sure stopping_method is checked and errors be thrown when
        a. setting it to valid when a validation set if not provided;
        b. setting it to xval when no cross validation is enabled;
    2. when stopping_method is set to xval, the scoring history should be the same as if not stopping_method is
    chosen by cross-validation was enabled.  Compare the two and make sure they are the same
    3. when the stopping_method is set to valid, the scoring history should be the same as if not stopping_method is
    chosen, cross-validation is disabled, a validation is provided.  Compare the two and make sure they are the same
    4. when the stopping_method is set to train, the scoring history should be the same as if not stopping_method is
    chosen, no validation is provided.  Compare the two and make sure they are the same
'''

model_runtime = []  # store actual model runtime in seconds
model_maxRuntime = []   # store given maximum runtime restrictions placed on building models for different algos
algo_names =[]
actual_model_runtime = []   # in seconds
model_runtime_overrun = []  # % by which the model runtime exceeds the maximum runtime.
model_within_max_runtime = []
err_bound = 0.5              # fractor by which we allow the model runtime over-run to be

def test_deeplearning_stopping_method():
    '''
    This pyunit test is written to ensure that the max_runtime_secs can restrict the model training time for all
    h2o algos.  See PUBDEV-4702.
    '''
    global model_within_max_runtime
    global err_bound
    seed = 12345

    # deeplearning
    training1_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training1_set.csv"))
    validation_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gridsearch/gaussian_training2_set.csv"))
    y_index = training1_data.ncol-1
    x_indices = list(range(y_index))
    model_auto_CV = H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[3], stopping_rounds=3,
                                             stopping_tolerance=0.01, nfolds=3)
    model_auto_noCV = H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[3], stopping_rounds=3,
                                               stopping_tolerance=0.01)
    model_noCV_valid = H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[3], stopping_method="valid",
                                     stopping_rounds=3, stopping_tolerance=0.01)
    model_CV = H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[3], stopping_method="valid",
                                          stopping_rounds=3, stopping_tolerance=0.01, nfolds=3)
    testStoppingMethod(0, training1_data, x_indices, y_index, validation_data)



def testStoppingMethod(model_index, training_data, x_indices, y_index, validation_data):
    '''
    This function will test if the stopping_method has been implemented correctly for the model type what was
    sent.  In particular, it will perform the following tests:
    1. Check to make sure stopping_method is checked and errors be thrown when
        a. setting it to valid when a validation set if not provided;
        b. setting it to xval when no cross validation is enabled;
    2. when stopping_method is set to xval, the scoring history should be the same as if not stopping_method is
    chosen by cross-validation was enabled.  Compare the two and make sure they are the same
    3. when the stopping_method is set to valid, the scoring history should be the same as if not stopping_method is
    chosen, cross-validation is disabled, a validation is provided.  Compare the two and make sure they are the same
    4. when the stopping_method is set to train, the scoring history should be the same as if not stopping_method is
    chosen, no validation is provided.  Compare the two and make sure they are the same

    :param model_index: choose model to be tested, 0 for deeplearning, 1 for GBM or 2 for RF
    :param training_data: training data frame
    :param x_indices: predictor column lists
    :param y_index: response column
    :param validation_data: validation dataset
    :return: pass or fail the test
    '''
    models_valid = ["H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[3], stopping_method='valid', stopping_rounds=3, stopping_tolerance=0.01, nfolds=3)"]
    models_train = ["H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[3], stopping_method='train', stopping_rounds=3, stopping_tolerance=0.01, nfolds=3)"]
    models_xval = ["H2ODeepLearningEstimator(distribution='gaussian', seed=seed, hidden=[3], stopping_method='xval', stopping_rounds=3, stopping_tolerance=0.01, nfolds=3)"]
    models_auto_CV = ["H2ODeepLearningEstimator(distribution='gaussian', seed=123456, hidden=[3], stopping_rounds=3, stopping_tolerance=0.01, nfolds=3)"]
    models_auto_noCV = ["H2ODeepLearningEstimator(distribution='gaussian', seed=123456, hidden=[3], stopping_rounds=3, stopping_tolerance=0.01)"]

    model.train(x=x_indices, y=y_index, training_frame=training_data, validation_frame=validation_data)


    actual_model_runtime.append(model._model_json["output"]["run_time"]/1000.0)
    # capture model runtime with




if __name__ == "__main__":
    pyunit_utils.standalone_test(test_deeplearning_stopping_method)
else:
    test_deeplearning_stopping_method()