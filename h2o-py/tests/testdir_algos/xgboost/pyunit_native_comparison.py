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

    # Cross-check H2O's predictions against native xgboost. We do NOT hand-roll a raw
    # native param dict (the old approach), because native xgboost 2.0+ auto-computes
    # base_score from the label mean and tweaks other init defaults, so a hand-built
    # dict diverges from H2O's bundled xgboost4j 1.6 and forced an xgboost>=2 skip.
    #
    # Instead use the documented bridge that the pyunit_PUBDEV_5777 / pyunit_H2OXGBoost_native_*
    # comparison tests use: convert_H2OXGBoostParams_2_XGBoostParams() reads H2O's resolved
    # native_parameters and pins base_score=0.5 to reproduce xgboost4j 1.6 behavior, and
    # convert_H2OFrame_2_DMatrix() reproduces H2O's categorical encoding. This keeps the
    # value comparison meaningful on every supported xgboost version (1.x and 2.x/3.x), so
    # no version skip and no co-installed xgboost 1.7.6 are needed.
    import xgboost as xgb
    native_params, num_boost_round = h2o_model.convert_H2OXGBoostParams_2_XGBoostParams()
    dmatrix = frame.convert_H2OFrame_2_DMatrix(['ownsTesla'], 'wealthy', h2o_model)
    bst = xgb.train(params=native_params, dtrain=dmatrix, num_boost_round=num_boost_round)
    native_prediction = bst.predict(data=dmatrix, iteration_range=(0, num_boost_round))
    print(native_prediction)
    assert len(native_prediction) == 5

    # Native binary:logistic returns P(positive class); with y='wealthy' (factor domain
    # ["0", "1"]) that is the probability of level "1", i.e. H2O's p1 column.
    for i in range(5):
        assert round(h2o_prediction['p1'][i, 0], 5) == round(native_prediction[i].item(), 5)


if __name__ == "__main__":
    pyunit_utils.standalone_test(arlines_test)
else:
    arlines_test()
