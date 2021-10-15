from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
import tempfile
from tests import pyunit_utils
from h2o.estimators.maxrglm import H2OMaxRGLMEstimator as maxrglm

# This is the serialization and de-serialization test suggested by Michalk.
def test_maxrglm_gaussian_model_id():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    maxrglm_model = maxrglm(seed=12345, max_predictor_number=7)
    maxrglm_model.train(training_frame=d, x=my_x, y=my_y)
    tmpdir = tempfile.mkdtemp()
    model_path = maxrglm_model.download_model(tmpdir)
    
    h2o.remove_all()
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    loaded_maxrglm_model = h2o.load_model(model_path)    
    resultFrame = loaded_maxrglm_model.result()
    numRows = resultFrame.nrows
    modelIDs = loaded_maxrglm_model._model_json["output"]["best_model_ids"]
    for ind in list(range(numRows)):
        model_from_frame = h2o.get_model(resultFrame["model_id"][ind, 0])
        pred_frame = model_from_frame.predict(d)
        model_from_id = h2o.get_model(modelIDs[ind]['name'])
        pred_id = model_from_id.predict(d)
        pyunit_utils.compare_frames_local(pred_frame, pred_id, prob=1)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrglm_gaussian_model_id)
else:
    test_maxrglm_gaussian_model_id()
