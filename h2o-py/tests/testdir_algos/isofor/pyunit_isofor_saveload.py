import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.isolation_forest import H2OIsolationForestEstimator


def isolation_forest_save_and_load():
    print("Isolation Forest Smoke Test")

    train = h2o.import_file(pyunit_utils.locate("smalldata/anomaly/ecg_discord_train.csv"))

    if_model = H2OIsolationForestEstimator(ntrees=7, seed=12, sample_size=5)
    if_model.train(training_frame=train)

    path = pyunit_utils.locate("results")

    assert os.path.isdir(path), "Expected save directory {0} to exist, but it does not.".format(path)
    model_path = h2o.save_model(if_model, path=path, force=True)

    assert os.path.isfile(model_path), "Expected load file {0} to exist, but it does not.".format(model_path)
    reloaded = h2o.load_model(model_path)

    assert isinstance(reloaded, H2OIsolationForestEstimator), "Expected and H2OIsolationForestEstimator, but got {0}"\
        .format(reloaded)

if __name__ == "__main__":
    pyunit_utils.standalone_test(isolation_forest_save_and_load)
else:
    isolation_forest_save_and_load()
