from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators.extended_isolation_forest import H2OExtendedIsolationForestEstimator


def extended_isolation_forest_metrics_large():
    print("Extended Isolation Forest Anomaly Metrics Test On Large Data")

    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/creditcardfraud/creditcardfraud.csv"))

    eif_model = H2OExtendedIsolationForestEstimator(ntrees=100, seed=0xBEEF, sample_size=256, extension_level=1)
    eif_model.train(training_frame=train)
    metrics_by_python = eif_model.predict(train).mean()
    average_mean_length = metrics_by_python[0, 1]
    average_anomaly_score = metrics_by_python[0, 0]

    print(metrics_by_python)
    print(eif_model)

    perf = eif_model.model_performance()
    assert_equals(perf.mean_score(), average_mean_length, "Mean score metric is not correct", 1e-3)
    assert_equals(perf.mean_normalized_score(), average_anomaly_score, "Anomaly score metric is not correct", 1e-3)


if __name__ == "__main__":
    pyunit_utils.standalone_test(extended_isolation_forest_metrics_large)
else:
    extended_isolation_forest_metrics_large()
