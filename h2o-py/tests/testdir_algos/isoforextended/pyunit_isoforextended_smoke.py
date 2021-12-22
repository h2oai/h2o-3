from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.extended_isolation_forest import H2OExtendedIsolationForestEstimator


def extended_isolation_forest():
    print("Extended Isolation Forest Smoke Test")

    train = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/single_blob.csv"))

    eif_model = H2OExtendedIsolationForestEstimator(ntrees=99, seed=0xBEEF, sample_size=255, extension_level=1)
    eif_model.train(training_frame=train)
    anomaly_score = eif_model.predict(train)
    anomaly = anomaly_score['anomaly_score'].as_data_frame(use_pandas=True)["anomaly_score"]

    assert 99 == eif_model._model_json["output"]["model_summary"]["number_of_trees"][0], "Python API is not working!"
    assert 255 == eif_model._model_json["output"]["model_summary"]["size_of_subsample"][0], "Python API is not working!"
    assert 1 == eif_model._model_json["output"]["model_summary"]["extension_level"][0], "Python API is not working!"

    print(anomaly_score)
    print(eif_model)

    assert 0.36 < anomaly.mean() < 0.40, \
        "Not expected output: Mean anomaly score is suspiciously different. " + str(anomaly.mean())

    # The output of the EIF algorithm is based on randomly generated values. 
    # If the randomization is changed, then the output can be slightly different and it is fine to update them.
    # The link to source paper: https://arxiv.org/pdf/1811.02141.pdf
    assert anomaly[0] >= 0.70, \
        "Not expected output: Anomaly point should have higher score " + str(anomaly[0])
    assert anomaly[5] <= 0.55, \
        "Not expected output: Anomaly point should have higher score about 0.5 " + str(anomaly[5])
    assert anomaly[33] <= 0.55, \
        "Not expected output: Anomaly point should have higher score about 0.5 " + str(anomaly[33])
    assert anomaly[256] <= 0.55, \
        "Not expected output: Anomaly point should have higher score about 0.5 " + str(anomaly[256])
    assert anomaly[499] <= 0.55, \
        "Not expected output: Anomaly point should have higher score about 0.5 " + str(anomaly[499])


if __name__ == "__main__":
    pyunit_utils.standalone_test(extended_isolation_forest)
else:
    extended_isolation_forest()
