import pandas as pd
import xgboost as xgb
from sklearn.preprocessing import LabelEncoder

from h2o.estimators.xgboost import *
from tests import pyunit_utils


def arlines_test():
    assert H2OXGBoostEstimator.available() is True

    # Artificial data to be used throughout the test, very simpleé
    raw_data = {'col1': [float('NaN'), 1, 0],
                'response': [20, 30, 40]}
    train_frame = pd.DataFrame(data = raw_data)

    prediction_data = {'col1': [0, 1, float('NaN')]}
    train_frame_1= pd.DataFrame(data=prediction_data)

    frame = h2o.H2OFrame(train_frame)
    frame['col1'] = frame['col1'].asnumeric()
    frame['response'] = frame['response'].asfactor()
    test = h2o.H2OFrame(train_frame_1)
    test['col1'] = test['col1'].asnumeric()

    # The ntrees parameters in H2O translates to max_depth param
    h2o_model = H2OXGBoostEstimator(training_frame=frame, learn_rate = 1,
                                booster='gbtree', seed=1, ntrees=2, dmatrix_type='sparse')
    h2o_model.train(x=['col1'], y='response', training_frame=frame)
    print(h2o_model)
    h2o_prediction = h2o_model.predict(test['col1'])
    print(h2o_prediction)

    assert h2o_prediction['predict'][0, 0] == 0
    assert h2o_prediction['predict'][1, 0] == 1
    assert h2o_prediction['predict'][2, 0] == 2


if __name__ == "__main__":
    pyunit_utils.standalone_test(arlines_test)
else:
    arlines_test()
