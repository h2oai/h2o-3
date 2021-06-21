from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from sklearn.datasets import make_blobs
from tests import pyunit_utils
from h2o.estimators.extended_isolation_forest import H2OExtendedIsolationForestEstimator


def extended_isolation_forest_extension_level_smoke():
    """
    Test extension_level parameter of Extended Isolation Forest.
    The extension_level=0 means Isolation Forest's behaviour. This test is testing the known Isolation Forest's
    behaviour of 'Ghost clusters' which Extended Isolation Forest mitigates. The overall variance in anomaly score of
    EIF should be lower than anomaly score IF. Anomaly score in 'Ghost clusters' should be lower for IF.
    For more information see source paper https://arxiv.org/pdf/1811.02141.pdf (Figure 2).
    """

    seed = 0xBEEF
    double_blob = make_blobs(centers=[[10, 0], [0, 10]], cluster_std=[1, 1], random_state=seed,
                             n_samples=500, n_features=2)[0]
    train = h2o.H2OFrame(double_blob)
    anomalies = h2o.H2OFrame([[0, 0], [10, 10]])  # Points in the ghost clusters

    eif_model = H2OExtendedIsolationForestEstimator(ntrees=100, seed=seed, sample_size=255, extension_level=1)
    eif_model.train(training_frame=train)
    eif_overall_anomaly_score = eif_model.predict(train)
    eif_overall_anomaly = eif_overall_anomaly_score['anomaly_score'].as_data_frame(use_pandas=True)["anomaly_score"]

    if_model = H2OExtendedIsolationForestEstimator(ntrees=100, seed=0xBEEF, sample_size=255, extension_level=0)
    if_model.train(training_frame=train)
    if_overall_anomaly_score = if_model.predict(train)
    if_overall_anomaly = if_overall_anomaly_score['anomaly_score'].as_data_frame(use_pandas=True)["anomaly_score"]

    eif_anomaly_score = eif_model.predict(anomalies)['anomaly_score'].as_data_frame(use_pandas=True)["anomaly_score"]
    if_anomaly_score = if_model.predict(anomalies)['anomaly_score'].as_data_frame(use_pandas=True)["anomaly_score"]

    assert if_anomaly_score[0] < eif_anomaly_score[0], \
        "The anomaly score of simulated Isolation Forest's should be significantly lower than score of " \
        "Extended Isolation Forest because this point is in 'Ghost cluster'. " + str(if_anomaly_score[0]) + " < " \
        + str(eif_anomaly_score[0])

    assert if_anomaly_score[1] < eif_anomaly_score[1], \
        "The anomaly score of simulated Isolation Forest's should be significantly lower than score of " \
        "Extended Isolation Forest because this point is in 'Ghost cluster'. " + str(if_anomaly_score[1]) + " < " \
        + str(eif_anomaly_score[1])

    assert 0.0015 < eif_overall_anomaly.var() < 0.0020 < if_overall_anomaly.var() < 0.0023, \
        "Not expected output: Variance in anomaly score of Extended Isolation Forest is suspiciously different from " \
        "Isolation Forest (EIF with extension_level=0). In general, the overall variance in anomaly score of EIF " \
        "should be lower than variance in score of IF. It could be potential bug in extension_level parameter " \
        "handling because " + str(eif_overall_anomaly.var()) + " should be lower than " + str(if_overall_anomaly.var())


if __name__ == "__main__":
    pyunit_utils.standalone_test(extended_isolation_forest_extension_level_smoke)
else:
    extended_isolation_forest_extension_level_smoke()
