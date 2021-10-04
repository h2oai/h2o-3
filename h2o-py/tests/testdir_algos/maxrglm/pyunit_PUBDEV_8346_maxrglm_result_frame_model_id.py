from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.maxrglm import H2OMaxRGLMEstimator as maxrglm

# test maxrglm algorithm for regression only.  Make sure the result frame contains the correct information.  Make
# sure that we can instantiate the best model from model ID, or from the best_model_ids in the model output.  Prediction
# frames from both models are compared.
def test_maxrglm_gaussian_model_id():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    maxrglm_model = maxrglm(seed=12345, max_predictor_number=7)
    maxrglm_model.train(training_frame=d, x=my_x, y=my_y)
    resultFrame = maxrglm_model.result()
    numRows = resultFrame.nrows
    modelIDs = maxrglm_model._model_json["output"]["best_model_ids"]
    for ind in list(range(numRows)):
        model_frame = h2o.get_model(resultFrame["model_id"][ind, 0])
        pred_frame = model_frame.predict(d)
        model_id = h2o.get_model(modelIDs[ind]['name'])
        pred_id = model_id.predict(d)
        pyunit_utils.compare_frames_local(pred_frame, pred_id, prob=1)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrglm_gaussian_model_id)
else:
    test_maxrglm_gaussian_model_id()
