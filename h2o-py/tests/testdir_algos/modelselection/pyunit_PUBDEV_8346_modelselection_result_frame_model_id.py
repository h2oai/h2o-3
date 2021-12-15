from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test modelselection algorithm for regression only.  Make sure the result frame contains the correct information.  Make
# sure that we can instantiate the best model from model ID, or from the best_model_ids in the model output.  Prediction
# frames from both models are compared.
def test_modelselection_gaussian_model_id():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    allsubsets_model = modelSelection(seed=12345, max_predictor_number=7, mode="allsubsets")
    allsubsets_model.train(training_frame=d, x=my_x, y=my_y)
    result_frame_allsubsets = allsubsets_model.result()
    numRows = result_frame_allsubsets.nrows
    modelIDs_allsubsets = allsubsets_model._model_json["output"]["best_model_ids"]
    maxr_model = modelSelection(seed=12345, max_predictor_number=7, mode="maxr")
    maxr_model.train(training_frame=d, x=my_x, y=my_y)
    result_frame_maxr = maxr_model.result()
    for ind in list(range(numRows)):
        model_from_frame_allsubsets = h2o.get_model(result_frame_allsubsets["model_id"][ind, 0])
        pred_frame_allsubsets = model_from_frame_allsubsets.predict(d)
        model_from_frame_allsubsets = h2o.get_model(modelIDs_allsubsets[ind]['name'])
        pred_id_allsubsets = model_from_frame_allsubsets.predict(d)
        pyunit_utils.compare_frames_local(pred_frame_allsubsets, pred_id_allsubsets, prob=1)
        model_from_frame_maxr = h2o.get_model(result_frame_maxr["model_id"][ind, 0])
        pred_frame_maxr = model_from_frame_maxr.predict(d)
        pyunit_utils.compare_frames_local(pred_frame_allsubsets, pred_frame_maxr, prob=1, tol=1e-6)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_gaussian_model_id)
else:
    test_modelselection_gaussian_model_id()
