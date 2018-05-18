import pandas as pd

from h2o.estimators.xgboost import *
from tests import pyunit_utils


def xgboost_categorical_zero_nan_handling_test():
    assert H2OXGBoostEstimator.available() is True


    raw_training_Data = {'col1': [0,float('NaN'), 1],
                'response': [20, 30, 40]}
    pandas_training_rame = pd.DataFrame(data = raw_training_Data)

    raw_prediction_data = {'col1': [0, 1, float('NaN')]}
    pandas_prediction_frame= pd.DataFrame(data=raw_prediction_data)

    training_Frame = h2o.H2OFrame(pandas_training_rame)
    training_Frame['col1'] = training_Frame['col1'].asfactor()
    training_Frame['response'] = training_Frame['response'].asnumeric()
    prediction_frame = h2o.H2OFrame(pandas_prediction_frame)
    prediction_frame['col1'] = prediction_frame['col1'].asfactor()

    #Learning rate 1 ensures failure if NaN is treated the same as zero
    sparse_trained_model = H2OXGBoostEstimator(training_frame=training_Frame, learn_rate = 1,
                                booster='gbtree', seed=1, ntrees=1, dmatrix_type='sparse')
    sparse_trained_model.train(x=['col1'], y='response', training_frame=training_Frame)
    sparse_based_prediction = sparse_trained_model.predict(prediction_frame['col1'])
    print(sparse_based_prediction)

    #Learning rate 1 ensures failure if NaN is treated the same as zero
    dense_trained_model = H2OXGBoostEstimator(training_frame=training_Frame, learn_rate = 1,
                                               booster='gbtree', seed=1, ntrees=1, dmatrix_type='dense')
    dense_trained_model.train(x=['col1'], y='response', training_frame=training_Frame)
    dense_based_prediction = dense_trained_model.predict(prediction_frame['col1'])
    print(dense_based_prediction)

    assert len(dense_based_prediction['predict']) == len(sparse_based_prediction['predict'])

    # Predictions by both models (sparse and dense) should be the same
    assert dense_based_prediction['predict'][0,0] == sparse_based_prediction['predict'][0,0]
    assert dense_based_prediction['predict'][1,0] == sparse_based_prediction['predict'][1,0]
    assert dense_based_prediction['predict'][2,0] == sparse_based_prediction['predict'][2,0]


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_categorical_zero_nan_handling_test)
else:
    xgboost_categorical_zero_nan_handling_test()
