import sys, os
sys.path.insert(1, "../../")
import h2o
from h2o.model.binomial import H2OBinomialModel

def save_load_model(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    prostate = h2o.import_frame(h2o.locate("smalldata/prostate/prostate.csv"))
    prostate["CAPSULE"] = prostate["CAPSULE"].asfactor()
    prostate_glm = h2o.glm(y=prostate["CAPSULE"], x=prostate[["AGE","RACE","PSA","DCAPS"]], family = "binomial",
                           alpha = [0.5])
    model_path = h2o.save_model(prostate_glm, name="delete_model", force=True)
    the_model = h2o.load_model(model_path)

    assert isinstance(the_model, H2OBinomialModel), "Expected and H2OBinomialModel, but got {0}".format(the_model)

if __name__ == "__main__":
    h2o.run_test(sys.argv, save_load_model)
