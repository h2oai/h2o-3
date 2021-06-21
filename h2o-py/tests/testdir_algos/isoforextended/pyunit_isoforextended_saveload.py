from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.extended_isolation_forest import H2OExtendedIsolationForestEstimator


def extended_isolation_forest_save_and_load():
    print("Extended Isolation Forest Save Load Test")
    
    train = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/single_blob.csv"))

    eif_model = H2OExtendedIsolationForestEstimator(ntrees=7, seed=12, sample_size=5)
    eif_model.train(training_frame=train)
    anomaly_score = eif_model.predict(train)
    anomaly = anomaly_score['anomaly_score'].as_data_frame(use_pandas=True)["anomaly_score"]

    path = pyunit_utils.locate("results")

    assert os.path.isdir(path), "Expected save directory {0} to exist, but it does not.".format(path)
    model_path = h2o.save_model(eif_model, path=path, force=True)

    assert os.path.isfile(model_path), "Expected load file {0} to exist, but it does not.".format(model_path)
    reloaded = h2o.load_model(model_path)
    anomaly_score_reloaded = reloaded.predict(train)
    anomaly_reloaded = anomaly_score_reloaded['anomaly_score'].as_data_frame(use_pandas=True)["anomaly_score"]

    assert isinstance(reloaded, 
                      H2OExtendedIsolationForestEstimator), \
        "Expected and H2OExtendedIsolationForestEstimator, but got {0}"\
        .format(reloaded)
    
    assert (anomaly[0] == anomaly_reloaded[0]), "Output is not the same after reload"
    assert anomaly[5] == anomaly_reloaded[5], "Output is not the same after reload"
    assert anomaly[33] == anomaly_reloaded[33], "Output is not the same after reload"
    assert anomaly[256] == anomaly_reloaded[256], "Output is not the same after reload"
    assert anomaly[499] == anomaly_reloaded[499], "Output is not the same after reload"


if __name__ == "__main__":
    pyunit_utils.standalone_test(extended_isolation_forest_save_and_load)
else:
    extended_isolation_forest_save_and_load()
