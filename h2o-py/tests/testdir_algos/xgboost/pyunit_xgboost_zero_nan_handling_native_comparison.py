import pandas as pd
import xgboost as xgb
import numpy as np;

from h2o.estimators.xgboost import *
from tests import pyunit_utils
from scipy.sparse import csr_matrix


def xgboost_categorical_zero_nan_handling_sparse_vs_native():
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
    predict_test_data_csr = csr_matrix(col1)


    # Native XGBosot model trained first
    dtrain = xgb.DMatrix(data=training_data_csr, label=training_data_label)
    dtest = xgb.DMatrix(data=predict_test_data_csr)
    runSeed = 10
    ntrees = 1
    h2oParamsS = {"ntrees":ntrees, "max_depth":4, "seed":runSeed, "learn_rate":1.0, "col_sample_rate_per_tree" : 1.0,
                  "min_rows" : 1, "score_tree_interval": ntrees+1, "dmatrix_type":"dense", "tree_method": "auto", "backend": "cpu"}
    nativeParam = {'colsample_bytree': h2oParamsS["col_sample_rate_per_tree"],
                   'tree_method': 'auto',
                   'seed': h2oParamsS["seed"],
                   'booster': 'gbtree',
                   'objective': 'reg:linear',
                   'lambda': 0.0,
                   'eta': h2oParamsS["learn_rate"],
                   'grow_policy': 'depthwise',
                   'alpha': 0.0,
                   'subsample': 1.0,
                   'colsample_bylevel': 1.0,
                   'max_delta_step': 0.0,
                   'min_child_weight': h2oParamsS["min_rows"],
                   'gamma': 0.0,
                   'max_depth': h2oParamsS["max_depth"]}

    bst = xgb.train(params=nativeParam, dtrain=dtrain, num_boost_round=10)
    native_prediction = bst.predict(data=dtest)


    pandas_training_frame = pd.DataFrame(data=raw_training_data)

    training_frame = h2o.H2OFrame(pandas_training_frame)
    training_frame['col1'] = training_frame['col1'].asnumeric()
    training_frame['response'] = training_frame['response'].asnumeric()
    prediction_frame = h2o.H2OFrame(prediction_frame)
    prediction_frame['col1'] = prediction_frame['col1'].asnumeric()

    sparse_trained_model = H2OXGBoostEstimator(**h2oParamsS)
    sparse_trained_model.train(x=['col1'], y='response', training_frame=training_frame)
    sparse_based_prediction = sparse_trained_model.predict(prediction_frame['col1'])

    #Prediction should be equal. In both cases, 0 and NaN should be treated the same (default direction for NaN)
    print(native_prediction)
    print(sparse_based_prediction)
    assert sparse_based_prediction['predict'][0,0] == native_prediction[0].item()
    assert sparse_based_prediction['predict'][1,0] == native_prediction[1].item()
    assert sparse_based_prediction['predict'][2,0] == native_prediction[2].item()


if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_categorical_zero_nan_handling_sparse_vs_native)
else:
    xgboost_categorical_zero_nan_handling_sparse_vs_native()
