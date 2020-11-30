from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.xgboost import *
import numpy as np

#testing default setup of following parameters:
#distribution (available in Deep Learning, XGBoost, GBM):
#stopping_metric (available in: GBM, DRF, Deep Learning, AutoML, XGBoost, Isolation Forest):
#histogram_type (available in: GBM, DRF)
#solver (available in: GLM) already done in hex.glm.GLM.defaultSolver()
#categorical_encoding (available in: GBM, DRF, Deep Learning, K-Means, Aggregator, XGBoost, Isolation Forest)
#fold_assignment (available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost)

def test_xgboost_effective_parameters():
    assert H2OXGBoostEstimator.available()

    prostate_frame = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate.csv'))
    x = ['RACE']
    y = 'CAPSULE'
    prostate_frame[y] = prostate_frame[y].asfactor()
    prostate_frame.split_frame(ratios=[0.75], destination_frames=['prostate_training', 'prostate_validation'], seed=1)
    training_frame = h2o.get_frame('prostate_training')
    test_frame = h2o.get_frame('prostate_validation')

    xgb1 = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.7, booster='gbtree', seed=1, ntrees=2, stopping_rounds=5)
    xgb1.train(x=x, y=y, training_frame=training_frame, validation_frame=test_frame)

    xgb2 = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.7, booster='gbtree', seed=1, ntrees=2, distribution="bernoulli",
                               categorical_encoding="OneHotInternal", stopping_rounds =5, stopping_metric='logloss')
    xgb2.train(x=x, y=y, training_frame=training_frame, validation_frame=test_frame)

    assert xgb1.parms['distribution']['input_value'] == 'AUTO'
    assert xgb1.parms['distribution']['actual_value'] == xgb2.parms['distribution']['actual_value']
    np.testing.assert_almost_equal(xgb1.logloss(), xgb2.logloss())
    assert xgb1.parms['stopping_metric']['input_value'] == 'AUTO'
    assert xgb1.parms['stopping_metric']['actual_value'] == xgb2.parms['stopping_metric']['actual_value']
    assert xgb1.parms['categorical_encoding']['input_value'] == 'AUTO'
    assert xgb1.parms['categorical_encoding']['actual_value'] == xgb2.parms['categorical_encoding']['actual_value']
    assert xgb1.parms['fold_assignment']['input_value'] == 'AUTO'
    assert xgb1.parms['fold_assignment']['actual_value'] is None


    xgb1 = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.7, booster='gbtree', seed=1, ntrees=2, nfolds=5)
    xgb1.train(x=x, y=y, training_frame=training_frame)

    xgb2 = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.7, booster='gbtree', seed=1, ntrees=2, distribution="bernoulli",
                               categorical_encoding="OneHotInternal", nfolds=5, fold_assignment="Random")
    xgb2.train(x=x, y=y, training_frame=training_frame)

    assert xgb1.parms['distribution']['input_value'] == 'AUTO'
    assert xgb1.parms['distribution']['actual_value'] == xgb2.parms['distribution']['actual_value']
    np.testing.assert_almost_equal(xgb1.logloss(), xgb2.logloss())
    assert xgb1.parms['stopping_metric']['input_value'] == 'AUTO'
    assert xgb1.parms['stopping_metric']['actual_value'] is None
    assert xgb1.parms['categorical_encoding']['input_value'] == 'AUTO'
    assert xgb1.parms['categorical_encoding']['actual_value'] == xgb2.parms['categorical_encoding']['actual_value']
    assert xgb1.parms['fold_assignment']['input_value'] == 'AUTO'
    assert xgb1.parms['fold_assignment']['actual_value'] == xgb2.parms['fold_assignment']['actual_value']
    
    try:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "false"))
        xgb1 = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.7, booster='gbtree', seed=1, ntrees=2, nfolds=5)
        xgb1.train(x=x, y=y, training_frame=training_frame)

        xgb2 = H2OXGBoostEstimator(training_frame=training_frame, learn_rate=0.7, booster='gbtree', seed=1, ntrees=2, distribution="bernoulli",
                               categorical_encoding="OneHotInternal", nfolds=5, fold_assignment="Random")
        xgb2.train(x=x, y=y, training_frame=training_frame)

        assert xgb1.parms['distribution']['input_value'] == 'AUTO'
        assert xgb1.parms['distribution']['actual_value'] == 'AUTO'
        np.testing.assert_almost_equal(xgb1.logloss(), xgb2.logloss())
        assert xgb1.parms['stopping_metric']['input_value'] == 'AUTO'
        assert xgb1.parms['stopping_metric']['actual_value'] == 'AUTO'
        assert xgb1.parms['categorical_encoding']['input_value'] == 'AUTO'
        assert xgb1.parms['categorical_encoding']['actual_value'] == 'AUTO'
        assert xgb1.parms['fold_assignment']['input_value'] == 'AUTO'
        assert xgb1.parms['fold_assignment']['actual_value'] == 'AUTO'
    finally:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "true"))


if __name__ == "__main__":
  pyunit_utils.standalone_test(test_xgboost_effective_parameters)
else:
    test_xgboost_effective_parameters()
