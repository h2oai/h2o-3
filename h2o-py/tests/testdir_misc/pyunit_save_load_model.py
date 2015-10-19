import os
import h2o, tests
from h2o.model.binomial import H2OBinomialModel

def save_load_model():

    prostate = h2o.import_file(tests.locate("smalldata/prostate/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    prostate_glm = h2o.glm(y=prostate["CAPSULE"], x=prostate[["AGE","RACE","PSA","DCAPS"]], family = "binomial",
                           alpha = [0.5])

    path = tests.locate("results")

    assert os.path.isdir(path), "Expected save directory {0} to exist, but it does not.".format(path)
    model_path = h2o.save_model(prostate_glm, path=path, force=True)

    assert os.path.isdir(model_path), "Expected load directory {0} to exist, but it does not.".format(model_path)
    the_model = h2o.load_model(model_path)

    assert isinstance(the_model, H2OBinomialModel), "Expected and H2OBinomialModel, but got {0}".format(the_model)


pyunit_test = save_load_model
