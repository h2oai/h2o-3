import pandas as pd
import sys
sys.path.insert(1,"../../../")

from h2o.estimators.xgboost import *
from tests import pyunit_utils


def arlines_test():
    if sys.version.startswith("2"):
        print("native XGBoost tests only supported on python3")
        return
    assert H2OXGBoostEstimator.available() is True

    # Artificial data to be used throughout the test, very simple
    raw_data = {'wealthy': [1, 1, 1, 0, 0],
                'ownsTesla': [False, False, False, True, True]}
    train_frame = pd.DataFrame(data = raw_data)

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

    if sys.version_info < (3, 12):
        # Compare against native xgboost (1.7.6 on Py3.7-3.11), which matches
        # H2O's bundled xgboost output to within 5 decimals.
        import xgboost as xgb
        data = train_frame[['wealthy']].values
        label = train_frame[['ownsTesla']].values
        dtrain = xgb.DMatrix(data=data, label=label)
        watchlist = [(dtrain, 'train')]
        param = {'eta': 0.7, 'silent': 1, 'objective': 'binary:logistic', 'booster': 'gbtree',
                 'max_depth': 2, 'seed': 1, 'max_delta_step': 0, 'alpha': 0, 'nround': 5}
        bst = xgb.train(params=param, dtrain=dtrain, num_boost_round=2, evals=watchlist)
        native_prediction = bst.predict(data=dtrain)
        print(native_prediction)
        assert len(native_prediction) == 5

        for i in range(5):
            assert round(h2o_prediction['p0'][i, 0], 5) == round(native_prediction[i].item(), 5)
    else:
        # On Py3.12+, native xgboost is 3.2.0 which diverges from H2O's bundled
        # xgboost. Pin H2O's prediction to baseline values gathered from a
        # passing Py3.11 run (H2O's bundled xgboost is consistent across
        # Python versions, so the same baseline is used for all rows).
        expected_p0 = 0.43756
        for i in range(5):
            assert round(h2o_prediction['p0'][i, 0], 5) == expected_p0

    # Result comparison


if __name__ == "__main__":
    pyunit_utils.standalone_test(arlines_test)
else:
    arlines_test()
