from tests import pyunit_utils
import sys, os
sys.path.insert(1, "../../")
import h2o
from h2o.estimators.estimator_base import H2OEstimator


def save_load_model():



    prostate = h2o.import_frame(pyunit_utils.locate("smalldata/prostate/prostate.csv"))

    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    prostate_glm = h2o.glm(y=prostate["CAPSULE"],
                           x=prostate[["AGE","RACE","PSA","DCAPS"]],
                           family = "binomial",
                           alpha = [0.5])

    path = pyunit_utils.locate("results")
    model_path = h2o.save_model(prostate_glm, path=path, force=True)
    the_model = h2o.load_model(model_path)

    assert isinstance(the_model,H2OEstimator), "Expected and H2OBinomialModel, but got {0}".format(the_model)

if __name__ == "__main__":
    pyunit_utils.standalone_test(save_load_model)
else:
    save_load_model()
