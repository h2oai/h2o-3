import pandas as pd
import sys
sys.path.insert(1,"../../../")

from h2o.estimators.xgboost import *
from tests import pyunit_utils


def arlines_test():
    if sys.version.startswith("2"):
        print("native XGBoost tests only supported on python3")
        return
    import xgboost as xgb
    assert H2OXGBoostEstimator.available() is True

    # Artificial data to be used throughout the test, very simple
    raw_data = {'wealthy': [1, 1, 1, 0, 0],
                'ownsTesla': [False, False, False, True, True]}
    train_frame = pd.DataFrame(data = raw_data)

    data = train_frame[['wealthy']].values
    label = train_frame[['ownsTesla']].values

    # Native XGBosot model trained first
    dtrain = xgb.DMatrix(data=data, label=label)
    watchlist = [(dtrain, 'train')]
    param = {'eta': 0.7, 'silent': 1, 'objective': 'binary:logistic', 'booster': 'gbtree',
             'max_depth': 2, 'seed': 1, 'max_delta_step': 0, 'alpha': 0, 'nround': 5}
    bst = xgb.train(params=param, dtrain=dtrain, num_boost_round=2, evals = watchlist)
    native_prediction = bst.predict(data=dtrain)
    print(native_prediction)
    assert len(native_prediction) == 5

    # H2O XGBoost model trained
    frame = h2o.H2OFrame(train_frame)
    # Force factor variables, even if recognized correctly
    frame['ownsTesla'] = frame['ownsTesla'].asfactor()
    frame['wealthy'] = frame['wealthy'].asfactor()
    # The ntrees parameters in H2O translates to max_depth param
    h2o_model = H2OXGBoostEstimator(training_frame=frame, learn_rate = 0.7,
                                booster='gbtree', seed=1, ntrees=2)
    h2o_model.train(x=['ownsTesla'], y='wealthy', training_frame=frame)
    h2o_prediction = h2o_model.predict(frame['ownsTesla'])
    print(h2o_prediction)

    assert len(h2o_prediction['p0']) == 5

    assert round(h2o_prediction['p0'][0,0],5) == round(native_prediction[0].item(), 5)
    assert round(h2o_prediction['p0'][1,0],5) == round(native_prediction[1].item(), 5)
    assert round(h2o_prediction['p0'][2,0],5) == round(native_prediction[2].item(), 5)
    assert round(h2o_prediction['p0'][3,0],5) == round(native_prediction[3].item(), 5)
    assert round(h2o_prediction['p0'][4,0],5) == round(native_prediction[4].item(), 5)

    # Result comparison


if __name__ == "__main__":
    pyunit_utils.standalone_test(arlines_test)
else:
    arlines_test()
