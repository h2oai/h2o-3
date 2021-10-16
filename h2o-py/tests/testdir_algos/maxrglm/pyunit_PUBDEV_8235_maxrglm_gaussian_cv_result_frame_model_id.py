from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.maxrglm import H2OMaxRGLMEstimator as maxrglm

# test maxrglm works with cross-validation, result frame and model id returns the right content
def test_maxrglm_cross_validation_result_frame_model_id():

    d = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    my_y = "C21"
    my_x = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16",
           "C17", "C18", "C19", "C20"]
    factorX = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for x in factorX:
        d[x] = d[x].asfactor()
    n_folds = 3

    maxrglm_model = maxrglm(seed=12345, max_predictor_number=3, nfolds=n_folds, fold_assignment="auto")
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
    pyunit_utils.standalone_test(test_maxrglm_cross_validation_result_frame_model_id)
else:
    test_maxrglm_cross_validation_result_frame_model_id()
