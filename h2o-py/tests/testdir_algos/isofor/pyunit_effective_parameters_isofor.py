from __future__ import print_function
import sys
import h2o
sys.path.insert(1,"../../../")
from tests import pyunit_utils
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator

#testing default setup of following parameters:
#distribution (available in Deep Learning, XGBoost, GBM):
#stopping_metric (available in: GBM, DRF, Deep Learning, AutoML, XGBoost, Isolation Forest):
#histogram_type (available in: GBM, DRF)
#solver (available in: GLM) already done in hex.glm.GLM.defaultSolver()
#categorical_encoding (available in: GBM, DRF, Deep Learning, K-Means, Aggregator, XGBoost, Isolation Forest)
#fold_assignment (available in: GBM, DRF, Deep Learning, GLM, Naïve-Bayes, K-Means, XGBoost)


def test_isolation_forrest_effective_parameters():
    train2 = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/ecg_discord_train.csv"))

    if1 = H2OIsolationForestEstimator(ntrees=7, seed=12, sample_size=5, stopping_rounds=3, score_each_iteration=True)
    if1.train(training_frame=train2)

    if2 = H2OIsolationForestEstimator(ntrees=7, seed=12, sample_size=5, stopping_rounds=3, stopping_metric = 'anomaly_score', categorical_encoding="Enum", score_each_iteration=True)
    if2.train(training_frame=train2)

    assert if1.parms['stopping_metric']['input_value'] == 'AUTO'
    assert if1.parms['stopping_metric']['actual_value'] == if2.parms['stopping_metric']['actual_value']
    assert if1._model_json['output']['training_metrics']._metric_json['mean_score'] == if2._model_json['output']['training_metrics']._metric_json['mean_score']
    assert if1.parms['categorical_encoding']['input_value'] == 'AUTO'
    assert if1.parms['categorical_encoding']['actual_value'] == if2.parms['categorical_encoding']['actual_value']
    
    try:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "false"))
        if1 = H2OIsolationForestEstimator(ntrees=7, seed=12, sample_size=5, stopping_rounds=3, score_each_iteration=True)
        if1.train(training_frame=train2)

        if2 = H2OIsolationForestEstimator(ntrees=7, seed=12, sample_size=5, stopping_rounds=3, stopping_metric = 'anomaly_score', categorical_encoding="Enum", score_each_iteration=True)
        if2.train(training_frame=train2)

        assert if1.parms['stopping_metric']['input_value'] == 'AUTO'
        assert if1.parms['stopping_metric']['actual_value'] == 'AUTO'
        assert if1._model_json['output']['training_metrics']._metric_json['mean_score'] == if2._model_json['output']['training_metrics']._metric_json['mean_score']
        assert if1.parms['categorical_encoding']['input_value'] == 'AUTO'
        assert if1.parms['categorical_encoding']['actual_value'] == 'AUTO'
    finally:
        h2o.rapids("(setproperty \"{}\" \"{}\")".format("sys.ai.h2o.algos.evaluate_auto_model_parameters", "true"))

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_isolation_forrest_effective_parameters)
else:
    test_isolation_forrest_effective_parameters()
