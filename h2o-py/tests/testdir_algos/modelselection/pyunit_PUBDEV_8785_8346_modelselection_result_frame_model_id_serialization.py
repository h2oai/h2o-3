from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
import tempfile
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# This is the serialization and de-serialization test suggested by Michalk.
def test_modelselection_serialization():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    allsubsets_model = modelSelection(seed=12345, max_predictor_number=7, mode="allsubsets")
    allsubsets_model.train(training_frame=d, x=my_x, y=my_y)
    tmpdir = tempfile.mkdtemp()
    model_path_allsubsets = allsubsets_model.download_model(tmpdir)
    maxr_model = modelSelection(seed=12345, max_predictor_number=7, mode="maxr")
    maxr_model.train(training_frame=d, x=my_x, y=my_y)
    model_path_maxr = maxr_model.download_model(tmpdir)

    maxrsweep_model = modelSelection(seed=12345, max_predictor_number=7, mode="maxrsweepsmall")
    maxrsweep_model.train(training_frame=d, x=my_x, y=my_y)
    model_path_maxrsweep = maxrsweep_model.download_model(tmpdir)

    maxrsweepfull_model = modelSelection(seed=12345, max_predictor_number=7, mode="maxrsweepfull")
    maxrsweepfull_model.train(training_frame=d, x=my_x, y=my_y)
    model_path_maxrsweepfull = maxrsweepfull_model.download_model(tmpdir)

    maxrsweephybrid_model = modelSelection(seed=12345, max_predictor_number=7, mode="maxrsweephybrid")
    maxrsweephybrid_model.train(training_frame=d, x=my_x, y=my_y)
    model_path_maxrsweephybrid = maxrsweephybrid_model.download_model(tmpdir)

    # make sure results returned by maxr and maxrsweep are the same
    pyunit_utils.compare_frames_local(maxr_model.result()[2:4], maxrsweep_model.result()[2:4], prob=1.0, tol=1e-5)
    pyunit_utils.compare_frames_local(maxr_model.result()[2:4], maxrsweepfull_model.result()[2:4], prob=1.0, tol=1e-5)
    pyunit_utils.compare_frames_local(maxr_model.result()[2:4], maxrsweephybrid_model.result()[2:4], prob=1.0, tol=1e-5)
    
    h2o.remove_all()
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    loaded_allsubsets_model = h2o.load_model(model_path_allsubsets)    
    result_frame_allsubsets = loaded_allsubsets_model.result()
    numRows = result_frame_allsubsets.nrows
    modelIDs_allsubsets = loaded_allsubsets_model._model_json["output"]["best_model_ids"]
    loaded_maxr_model = h2o.load_model(model_path_maxr)
    modelIDs_maxr = loaded_maxr_model._model_json["output"]["best_model_ids"]
    loaded_maxrsweep_model = h2o.load_model(model_path_maxrsweep)
    modelIDs_maxrsweep = loaded_maxrsweep_model._model_json["output"]["best_model_ids"]
    loaded_maxrsweepfull_model = h2o.load_model(model_path_maxrsweepfull)
    modelIDs_maxrsweepfull = loaded_maxrsweepfull_model._model_json["output"]["best_model_ids"]
    loaded_maxrsweephybrid_model = h2o.load_model(model_path_maxrsweephybrid)
    modelIDs_maxrsweephybrid = loaded_maxrsweephybrid_model._model_json["output"]["best_model_ids"]
    for ind in list(range(numRows)):
        model_from_frame_allsubsets = h2o.get_model(result_frame_allsubsets["model_id"][ind, 0])
        pred_frame_allsubsets = model_from_frame_allsubsets.predict(d)
        model_from_id_allsubsets = h2o.get_model(modelIDs_allsubsets[ind]['name'])
        pred_id_allsubsets = model_from_id_allsubsets.predict(d)
        pyunit_utils.compare_frames_local(pred_frame_allsubsets, pred_id_allsubsets, prob=1)
        model_from_id_maxr = h2o.get_model(modelIDs_maxr[ind]['name'])
        pred_id_maxr = model_from_id_maxr.predict(d)
        pyunit_utils.compare_frames_local(pred_frame_allsubsets, pred_id_maxr, prob=1)
        model_from_id_maxrsweep = h2o.get_model(modelIDs_maxrsweep[ind]['name'])
        pred_id_maxrsweep = model_from_id_maxrsweep.predict(d)
        pyunit_utils.compare_frames_local(pred_id_maxr, pred_id_maxrsweep, prob=1, tol=1e-6)
        model_from_id_maxrsweepfull = h2o.get_model(modelIDs_maxrsweepfull[ind]['name'])
        pred_id_maxrsweepfull = model_from_id_maxrsweepfull.predict(d)
        pyunit_utils.compare_frames_local(pred_id_maxr, pred_id_maxrsweepfull, prob=1, tol=1e-6)
        model_from_id_maxrsweephybrid = h2o.get_model(modelIDs_maxrsweephybrid[ind]['name'])
        pred_id_maxrsweephybrid = model_from_id_maxrsweephybrid.predict(d)
        pyunit_utils.compare_frames_local(pred_id_maxr, pred_id_maxrsweephybrid, prob=1, tol=1e-6)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_serialization)
else:
    test_modelselection_serialization()
