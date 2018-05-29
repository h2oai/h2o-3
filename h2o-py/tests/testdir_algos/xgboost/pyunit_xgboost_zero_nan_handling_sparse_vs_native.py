import pandas as pd
import xgboost as xgb
import numpy as np;
import scipy;

from h2o.estimators.xgboost import *
from tests import pyunit_utils
from scipy.sparse import csr_matrix


def xgboost_categorical_zero_nan_handling_numerical_test():
    assert H2OXGBoostEstimator.available() is True

    # Artificial data to be used throughout the test, very simple
    raw_training_data = {'col1': [0, float('NaN'), 1],
                         'response': [20, 30, 40]}
    raw_prediction_data = {'col1': [0, 1,float('NaN')]}
    prediction_frame= pd.DataFrame(data=raw_prediction_data)

    # Native XGBoost training data
    col1 = np.matrix([[0],[float('NaN')],[1]])
    training_data_csr = csr_matrix(col1);
    training_data_label = [20, 30, 40]
    predict_test_data_csr = csr_matrix([[0], [1], [float('NaN')]])


    # Native XGBosot model trained first
    dtrain = xgb.DMatrix(data=training_data_csr, label=training_data_label)
    dtest = xgb.DMatrix(data=predict_test_data_csr)
    param = {'learning_rate': 1.0, 'silent': 1, 'objective': 'reg:linear', 'booster': 'gbtree',
             'max_depth': 2, 'seed': 1, 'lambda': 0, 'max_delta_step': 0, 'alpha': 0, 'nround': 1,
             'tree_method': 'exact', 'max_bins': 256, 'grow_policy': 'depthwise',
             'subsample': 1.0, 'colsample_bylevel': 1.0, 'min_child_weight': 1.0, 'gamma': 0.0}
    bst = xgb.train(params=param, dtrain=dtrain, num_boost_round=10)
    native_prediction = bst.predict(data=dtest)
    print(native_prediction)


    pandas_training_frame = pd.DataFrame(data=raw_training_data)

    training_frame = h2o.H2OFrame(pandas_training_frame)
    training_frame['col1'] = training_frame['col1']
    training_frame['response'] = training_frame['response']
    prediction_frame = h2o.H2OFrame(prediction_frame)
    prediction_frame['col1'] = prediction_frame['col1']

    sparse_trained_model = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=1, max_depth=2,
                                               booster='gbtree', seed=1, ntrees=1, dmatrix_type='sparse', stopping_rounds=0)
    sparse_trained_model.train(x=['col1'], y='response', training_frame=training_frame)
    sparse_based_prediction = sparse_trained_model.predict(prediction_frame['col1'])
    print(sparse_based_prediction)

    #Prediction should be equal. In both cases, 0 and NaN should be treated the same (default direction for NaN)
    assert sparse_based_prediction['predict'][0,0] == native_prediction[0].item()
    assert sparse_based_prediction['predict'][1,0] == native_prediction[1].item()
    assert sparse_based_prediction['predict'][2,0] == native_prediction[2].item()


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_categorical_zero_nan_handling_numerical_test)
else:
    xgboost_categorical_zero_nan_handling_numerical_test()
