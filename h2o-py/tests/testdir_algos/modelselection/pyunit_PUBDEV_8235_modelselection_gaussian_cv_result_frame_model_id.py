from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test modelselection works with cross-validation, result frame and model id returns the right content
def test_modelselection_cv_result_frame_model_id():

    d = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    my_y = "C21"
    my_x = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16",
           "C17", "C18", "C19", "C20"]
    factorX = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for x in factorX:
        d[x] = d[x].asfactor()
    n_folds = 3

    allsubsets_model = modelSelection(seed=12345, max_predictor_number=3, nfolds=n_folds, fold_assignment="auto", 
                                   mode="allsubsets")
    allsubsets_model.train(training_frame=d, x=my_x, y=my_y)
    result_frame_allsubsets = allsubsets_model.result()
    maxr_model = modelSelection(seed=12345, max_predictor_number=3, nfolds=n_folds, fold_assignment="auto",
                                      mode="maxr")
    maxr_model.train(training_frame=d, x=my_x, y=my_y)
    result_frame_maxr = maxr_model.result()
    
    numRows = result_frame_allsubsets.nrows
    modelIDs_allsubsets = allsubsets_model._model_json["output"]["best_model_ids"]
    modelIDs_maxr = maxr_model._model_json["output"]["best_model_ids"]
    for ind in list(range(numRows)):
        model_allsubsets_from_frame = h2o.get_model(result_frame_allsubsets["model_id"][ind, 0])
        pred_frame_allsubsets = model_allsubsets_from_frame.predict(d)
        model_allsubsets_from_id = h2o.get_model(modelIDs_allsubsets[ind]['name'])
        pred_id_allsubsets = model_allsubsets_from_id.predict(d)
        pyunit_utils.compare_frames_local(pred_frame_allsubsets, pred_id_allsubsets, prob=1)
        # compare results from maxr with allsubsets
        model_maxr_from_frame = h2o.get_model(result_frame_maxr["model_id"][ind, 0])
        pred_frame_maxr = model_maxr_from_frame.predict(d)
        model_maxrs_from_id = h2o.get_model(modelIDs_maxr[ind]['name'])
        pred_id_maxr = model_maxrs_from_id.predict(d)
        pyunit_utils.compare_frames_local(pred_frame_maxr, pred_id_maxr, prob=1)
        pyunit_utils.compare_frames_local(pred_frame_allsubsets, pred_id_maxr, prob=1)
     
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_cv_result_frame_model_id)
else:
    test_modelselection_cv_result_frame_model_id()
