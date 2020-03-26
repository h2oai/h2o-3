import sys

sys.path.insert(1,"../..")
import h2o
from tests import pyunit_utils
import os
from h2o.estimators import H2OGradientBoostingEstimator


def download_model():
    prostate = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()

    prostate_gbm = H2OGradientBoostingEstimator(distribution="bernoulli", ntrees=10, max_depth=8,
                                                min_rows=10, learn_rate=0.2)
    prostate_gbm.train(x=["AGE", "RACE", "PSA", "VOL", "GLEASON"],
                       y="CAPSULE", training_frame=prostate)
    
    path = pyunit_utils.locate("results")

    downloaded_model_path = prostate_gbm.download_model(path=path)
    assert os.path.isfile(downloaded_model_path), \
        "Expected load file {0} to exist, but it does not.".format(downloaded_model_path)
    
    loaded_model = h2o.load_model(downloaded_model_path)
    assert isinstance(loaded_model, H2OGradientBoostingEstimator), \
        "Expected an H2OGradientBoostingEstimator, but got {0}".format(downloaded_model_path)
    
    uploaded_model = h2o.upload_model(downloaded_model_path)
    assert isinstance(uploaded_model, H2OGradientBoostingEstimator), \
        "Expected an H2OGradientBoostingEstimator, but got {0}".format(downloaded_model_path)


if __name__ == "__main__":
    pyunit_utils.standalone_test(download_model)
else:
    download_model()
