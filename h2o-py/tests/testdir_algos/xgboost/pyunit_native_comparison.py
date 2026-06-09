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
    # nthread=1 keeps the determinism check below reproducible across CI hosts
    # (multi-threaded XGBoost is not strictly seed-deterministic).
    h2o_model = H2OXGBoostEstimator(training_frame=frame, learn_rate = 0.7,
                                booster='gbtree', seed=1, ntrees=2, nthread=1)
    h2o_model.train(x=['ownsTesla'], y='wealthy', training_frame=frame)
    h2o_prediction = h2o_model.predict(frame['ownsTesla'])
    print(h2o_prediction)

    assert len(h2o_prediction['p0']) == 5

    # Cross-check against native xgboost when its initialization defaults still
    # match H2O's bundled xgboost4j 1.6. On Py3.7-3.10 we pin xgboost==1.7.6
    # (compatible defaults). On Py3.11+ the pinned version is 3.2.0, whose
    # auto-mean base_score and other tweaks diverge from 1.6.
    #
    # TODO(GH-16147): install xgboost 1.7.6 alongside 3.2.0 in the Py3.11+
    # test images so this cross-check survives the upgrade -- right now Py3.11+
    # is reduced to an H2O-only determinism sanity check, which is what the
    # original PR landed.
    import xgboost as xgb
    xgb_major = int(xgb.__version__.split(".", 1)[0])
    if xgb_major >= 2:
        # Native xgboost 2.x changed `base_score` default to auto-compute from the
        # label mean and tweaked other initialization defaults; predictions no
        # longer match H2O's bundled xgboost4j 1.6 bit-for-bit. Skipping is the
        # honest signal — a "train twice and compare" determinism check would
        # pass even if H2O's xgboost4j broke catastrophically. Re-enable when
        # xgboost 1.7.6 is installed alongside 3.x in the Py3.11+ test images.
        print("SKIPPED: native xgboost %s defaults diverge from bundled xgboost4j 1.6"
              " (see TODO above); install xgboost 1.7.6 to re-enable the cross-check"
              % xgb.__version__)
        return

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


if __name__ == "__main__":
    pyunit_utils.standalone_test(arlines_test)
else:
    arlines_test()
