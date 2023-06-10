from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.fair_cut_forest import H2OFairCutForestEstimator


def fair_cut_forest():
    print("Fair Cut Forest Smoke Test")

    train = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/single_blob.csv"))

    fcf_model = H2OFairCutForestEstimator(ntrees=99, seed=0xBEEF, sample_size=255, extension_level=1, k_planes=1)
    fcf_model.train(training_frame=train)
    anomaly_score = fcf_model.predict(train)
    anomaly = anomaly_score['anomaly_score'].as_data_frame(use_pandas=True)["anomaly_score"]

    assert 99 == fcf_model._model_json["output"]["model_summary"]["number_of_trees"][0], "Python API is not working!"
    assert 255 == fcf_model._model_json["output"]["model_summary"]["size_of_subsample"][0], "Python API is not working!"
    assert 1 == fcf_model._model_json["output"]["model_summary"]["extension_level"][0], "Python API is not working!"

    print(len(anomaly_score))
    print(anomaly.describe())
    print(fcf_model)

    assert 0.55 < anomaly.mean() < 0.58, \
        "Not expected output: Mean anomaly score is suspiciously different. " + str(anomaly.mean())

    # The output of the FCF algorithm is based on randomly generated values. 
    # If the randomization is changed, then the output can be slightly different and it is fine to update them.
    assert anomaly[0] >= anomaly.mean() * 1.1, \
        "Not expected output: Anomaly point should have higher score than average" + str(anomaly[0])
    assert anomaly[4] <= anomaly.mean(), \
        "Not expected output: Normal points should have score lower than average " + str(anomaly[5])
    assert anomaly[32] <= anomaly.mean(), \
        "Not expected output: Normal points should have score lower than average " + str(anomaly[32])
    assert anomaly[196] <= anomaly.mean(), \
        "Not expected output: Normal points should have score lower than average " + str(anomaly[196])
    assert anomaly[499] <= anomaly.mean(), \
        "Not expected output: Normal points should have score lower than average " + str(anomaly[499])


if __name__ == "__main__":
    pyunit_utils.standalone_test(fair_cut_forest)
else:
    fair_cut_forest()
