import pandas as pd
import xgboost as xgb

from h2o.estimators.xgboost import *
from tests import pyunit_utils


def xgboost_categorical_zero_nan_handling_numerical_test():
    assert H2OXGBoostEstimator.available() is True

    # Artificial data to be used throughout the test, very simple
    raw_training_data = {'col1': [0, float('NaN'), 1],
                         'response': [20, 30, 40]}
    train_frame = pd.DataFrame(data=raw_training_data)

    data = train_frame.as_matrix(['col1'])
    label = train_frame.as_matrix(['response'])

    # Native XGBosot model trained first
    dtrain = xgb.DMatrix(data=data, label=label)
    watchlist = [(dtrain, 'train')]
    param = {'eta': 1, 'silent': 1, 'objective': 'reg:linear', 'booster': 'gbtree',
             'max_depth': 2, 'seed': 1, 'lambda': 0, 'max_delta_step': 0, 'alpha': 0, 'nround': 5}
    bst = xgb.train(params=param, dtrain=dtrain, num_boost_round=2, evals=watchlist)
    native_prediction = bst.predict(data=dtrain)
    print(native_prediction)


    pandas_training_rame = pd.DataFrame(data=raw_training_data)
    raw_prediction_data = {'col1': [0, 1, float('NaN')]}
    pandas_prediction_frame= pd.DataFrame(data=raw_prediction_data)

    training_frame = h2o.H2OFrame(pandas_training_rame)
    training_frame['col1'] = training_frame['col1'].asnumeric()
    training_frame['response'] = training_frame['response'].asnumeric()
    prediction_frame = h2o.H2OFrame(pandas_prediction_frame)
    prediction_frame['col1'] = prediction_frame['col1'].asnumeric()

    #Learning rate 1 ensures failure if NaN is treated the same as zero
    sparse_trained_model = H2OXGBoostEstimator(training_frame=training_frame, learn_rate = 1,
                                               booster='gbtree', seed=1, ntrees=1, dmatrix_type='sparse')
    sparse_trained_model.train(x=['col1'], y='response', training_frame=training_frame)
    sparse_based_prediction = sparse_trained_model.predict(prediction_frame['col1'])
    print(sparse_based_prediction)

    # Predictions by both models (sparse and dense) should be the same
    assert sparse_based_prediction['predict'][0,0] == native_prediction[0]
    assert sparse_based_prediction['predict'][1,0] == native_prediction[1]
    assert sparse_based_prediction['predict'][2,0] == native_prediction[2]

if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_categorical_zero_nan_handling_numerical_test)
else:
    xgboost_categorical_zero_nan_handling_numerical_test()
