from __future__ import print_function
import sys
import h2o
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import numpy as np

#testing default setup of following parameters:
#distribution (available in Deep Learning, XGBoost, GBM):
#stopping_metric (available in: GBM, DRF, Deep Learning, AutoML, XGBoost, Isolation Forest):
#histogram_type (available in: GBM, DRF)
#solver (available in: GLM) already done in hex.glm.GLM.defaultSolver()
#categorical_encoding (available in: GBM, DRF, Deep Learning, K-Means, Aggregator, XGBoost, Isolation Forest)
#fold_assignment (available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost)

def test_gbm_effective_parameters():
    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    cars["economy_20mpg"] = cars["economy_20mpg"].asfactor()
    cars["year"] = cars["year"].asfactor()
    predictors = ["displacement", "power", "weight", "acceleration", "year"]
    response = "economy_20mpg"
    train, valid = cars.split_frame(ratios=[.8], seed=1234)

    gbm1 = H2OGradientBoostingEstimator(seed=1234, stopping_rounds=3, score_tree_interval=5)
    gbm1.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

    gbm2 = H2OGradientBoostingEstimator(seed=1234, stopping_rounds=3, score_tree_interval=5, distribution="bernoulli", stopping_metric="logloss",
                                        histogram_type="UniformAdaptive", categorical_encoding="Enum")
    gbm2.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

    np.testing.assert_almost_equal(gbm1.logloss(), gbm2.logloss())
    assert gbm1.parms['distribution']['input_value'] == 'AUTO'
    assert gbm1.parms['distribution']['actual_value'] == gbm2.parms['distribution']['actual_value']
    assert gbm1.parms['stopping_metric']['input_value'] == 'AUTO'
    assert gbm1.parms['stopping_metric']['actual_value'] == gbm2.parms['stopping_metric']['actual_value']
    assert gbm1.parms['histogram_type']['input_value'] == 'AUTO'
    assert gbm1.parms['histogram_type']['actual_value'] == gbm2.parms['histogram_type']['actual_value']
    assert gbm1.parms['categorical_encoding']['input_value'] == 'AUTO'
    assert gbm1.parms['categorical_encoding']['actual_value'] == gbm2.parms['categorical_encoding']['actual_value']

    gbm1 = H2OGradientBoostingEstimator(seed = 1234, nfolds=5)
    gbm1.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

    gbm2 = H2OGradientBoostingEstimator(seed = 1234, nfolds=5, fold_assignment='Random', distribution="bernoulli",
                                        histogram_type="UniformAdaptive", categorical_encoding="Enum")
    gbm2.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

    np.testing.assert_almost_equal(gbm1.logloss(), gbm2.logloss())
    assert gbm1.parms['distribution']['input_value'] == 'AUTO'
    assert gbm1.parms['distribution']['actual_value'] == gbm2.parms['distribution']['actual_value']
    assert gbm1.parms['stopping_metric']['input_value'] == 'AUTO'
    assert gbm1.parms['stopping_metric']['actual_value'] is None
    assert gbm1.parms['histogram_type']['input_value'] == 'AUTO'
    assert gbm1.parms['histogram_type']['actual_value'] == gbm2.parms['histogram_type']['actual_value']
    assert gbm1.parms['fold_assignment']['input_value'] == 'AUTO'
    assert gbm1.parms['fold_assignment']['actual_value'] == gbm2.parms['fold_assignment']['actual_value']
    assert gbm1.parms['categorical_encoding']['input_value'] == 'AUTO'
    assert gbm1.parms['categorical_encoding']['actual_value'] == gbm2.parms['categorical_encoding']['actual_value']
   
    try:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "false"))

        gbm1 = H2OGradientBoostingEstimator(seed = 1234, nfolds=5)
        gbm1.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

        gbm2 = H2OGradientBoostingEstimator(seed = 1234, nfolds=5, fold_assignment='Random', distribution="bernoulli",
                                    histogram_type="UniformAdaptive", categorical_encoding="Enum")
        gbm2.train(x=predictors, y=response, training_frame=train, validation_frame=valid)

        np.testing.assert_almost_equal(gbm1.logloss(), gbm2.logloss())
        assert gbm1.parms['distribution']['input_value'] == 'AUTO'
        # distribution value was being set before
        assert gbm1.parms['distribution']['actual_value'] == gbm2.parms['distribution']['actual_value']
        assert gbm1.parms['stopping_metric']['input_value'] == 'AUTO'
        assert gbm1.parms['stopping_metric']['actual_value'] == 'AUTO'
        assert gbm1.parms['histogram_type']['input_value'] == 'AUTO'
        assert gbm1.parms['histogram_type']['actual_value'] == 'AUTO'
        assert gbm1.parms['fold_assignment']['input_value'] == 'AUTO'
        assert gbm1.parms['fold_assignment']['actual_value'] == 'AUTO'
        assert gbm1.parms['categorical_encoding']['input_value'] == 'AUTO'
        assert gbm1.parms['categorical_encoding']['actual_value'] == 'AUTO'
    finally:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "true"))

    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    frame.pop('ID')
    frame[frame['VOL'],'VOL'] = None
    frame[frame['GLEASON'],'GLEASON'] = None
    r = frame.runif()
    train = frame[r < 0.8]
    test = frame[r >= 0.8]

    gbm = H2OGradientBoostingEstimator(ntrees=5, max_depth=3)
    gbm.train(x=list(range(2,train.ncol)), y="CAPSULE", training_frame=train, validation_frame=test)

    assert gbm.parms['categorical_encoding']['input_value'] == 'AUTO'
    assert gbm.parms['categorical_encoding']['actual_value'] == 'Enum'

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_gbm_effective_parameters)
else:
    test_gbm_effective_parameters()
