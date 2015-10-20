<<<<<<< HEAD
import sys, os
sys.path.insert(1, "../../")
import h2o, tests
from h2o.estimators.estimator_base import H2OEstimator
=======
import os

from h2o.model.binomial import H2OBinomialModel
>>>>>>> 4ce985f40b6c8f18cf4c40ca27ba158ffd1f04f4

def save_load_model():

    prostate = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    prostate_glm = h2o.glm(y=prostate["CAPSULE"], x=prostate[["AGE","RACE","PSA","DCAPS"]], family = "binomial",
                           alpha = [0.5])

    path = pyunit_utils.locate("results")

    assert os.path.isdir(path), "Expected save directory {0} to exist, but it does not.".format(path)
    model_path = h2o.save_model(prostate_glm, path=path, force=True)

    assert os.path.isdir(model_path), "Expected load directory {0} to exist, but it does not.".format(model_path)
    the_model = h2o.load_model(model_path)

    assert isinstance(the_model, H2OEstimator), "Expected and H2OBinomialModel, but got {0}".format(the_model)


save_load_model()
