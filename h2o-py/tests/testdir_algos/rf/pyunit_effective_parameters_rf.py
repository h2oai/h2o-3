from __future__ import print_function
import sys
import h2o
import numpy as np

from h2o.backend import H2OCluster

sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.random_forest import H2ORandomForestEstimator

#testing default setup of following parameters:
#distribution (available in Deep Learning, XGBoost, GBM):
#stopping_metric (available in: GBM, DRF, Deep Learning, AutoML, XGBoost, Isolation Forest):
#histogram_type (available in: GBM, DRF)
#solver (available in: GLM) already done in hex.glm.GLM.defaultSolver()
#categorical_encoding (available in: GBM, DRF, Deep Learning, K-Means, Aggregator, XGBoost, Isolation Forest)
#fold_assignment (available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost)


def test_random_forrest_effective_parameters():
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
    frame["Angaus"] = frame["Angaus"].asfactor()
    frame["Weights"] = h2o.H2OFrame.from_python(abs(np.random.randn(frame.nrow, 1)).tolist())[0]
    train, calib = frame.split_frame(ratios=[.8], destination_frames=["eco_train", "eco_calib"], seed=42)

    rf1 = H2ORandomForestEstimator(ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5, weights_column="Weights",
                                   stopping_rounds = 3, calibrate_model=True, calibration_frame=calib, seed = 1234)
    rf1.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train)

    rf2 = H2ORandomForestEstimator(ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5, weights_column="Weights",
                                   stopping_rounds = 3, stopping_metric='logloss', calibrate_model=True, calibration_frame=calib,
                                   seed = 1234, categorical_encoding = 'Enum')
    rf2.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train)

    assert rf1.parms['stopping_metric']['input_value'] == 'AUTO'
    assert rf1.parms['stopping_metric']['actual_value'] ==  rf2.parms['stopping_metric']['actual_value']
    assert pyunit_utils.equals(rf1.logloss(), rf2.logloss())
    assert rf1.parms['distribution']['input_value'] == 'bernoulli'
    assert rf1.parms['distribution']['actual_value'] == rf2.parms['distribution']['actual_value']
    assert rf1.parms['categorical_encoding']['input_value'] == 'AUTO'
    assert rf1.parms['categorical_encoding']['actual_value'] == rf2.parms['categorical_encoding']['actual_value']
    assert rf1.parms['fold_assignment']['input_value'] == 'AUTO'
    assert rf1.parms['fold_assignment']['actual_value'] == None

    rf1 = H2ORandomForestEstimator(ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5, weights_column="Weights",
                                   nfolds = 5, calibrate_model=True, calibration_frame=calib, seed = 1234)
    rf1.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train)

    rf2 = H2ORandomForestEstimator(ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5, weights_column="Weights",
                                   nfolds=5, fold_assignment='Random', calibrate_model=True, calibration_frame=calib, seed = 1234,
                                   categorical_encoding = 'Enum')
    rf2.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train)

    assert rf1.parms['stopping_metric']['input_value'] == 'AUTO'
    assert rf1.parms['stopping_metric']['actual_value'] is None
    assert pyunit_utils.equals(rf1.logloss(), rf2.logloss())
    assert rf1.parms['distribution']['input_value'] == 'bernoulli'
    assert rf1.parms['distribution']['actual_value'] == rf2.parms['distribution']['actual_value']
    assert rf1.parms['fold_assignment']['input_value'] == 'AUTO'
    assert rf1.parms['fold_assignment']['actual_value'] == rf2.parms['fold_assignment']['actual_value']
    assert rf1.parms['categorical_encoding']['input_value'] == 'AUTO'
    assert rf1.parms['categorical_encoding']['actual_value'] == rf2.parms['categorical_encoding']['actual_value']

    try:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "false"))
        rf1 = H2ORandomForestEstimator(ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5, weights_column="Weights",
                                   nfolds = 5, calibrate_model=True, calibration_frame=calib, seed = 1234)
        rf1.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train)

        rf2 = H2ORandomForestEstimator(ntrees=100, distribution="bernoulli", min_rows=10, max_depth=5, weights_column="Weights",
                                   nfolds=5, fold_assignment='Random', calibrate_model=True, calibration_frame=calib, seed = 1234,
                                   categorical_encoding = 'Enum')
        rf2.train(x=list(range(2, train.ncol)), y="Angaus", training_frame=train)

        assert rf1.parms['stopping_metric']['input_value'] == 'AUTO'
        assert rf1.parms['stopping_metric']['actual_value'] == 'AUTO'
        assert pyunit_utils.equals(rf1.logloss(), rf2.logloss())
        assert rf1.parms['distribution']['input_value'] == 'bernoulli'
        assert rf1.parms['distribution']['actual_value'] == rf2.parms['distribution']['actual_value']
        assert rf1.parms['fold_assignment']['input_value'] == 'AUTO'
        assert rf1.parms['fold_assignment']['actual_value'] == 'AUTO'
        assert rf1.parms['categorical_encoding']['input_value'] == 'AUTO'
        assert rf1.parms['categorical_encoding']['actual_value'] == 'AUTO'
    finally:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "true"))

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_random_forrest_effective_parameters)
else:
    test_random_forrest_effective_parameters()
