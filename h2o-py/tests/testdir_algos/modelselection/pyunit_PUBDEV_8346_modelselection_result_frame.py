from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test modelselection algorithm for regression only.  Make sure the result frame contains the correct information.  Make
# sure that we can instantiate the best model from model ID, perform scoring with it.
def test_gaussian_result_frame_model_id():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    
    maxr_model = modelSelection(seed=12345, max_predictor_number=7, mode="maxr")
    maxr_model.train(training_frame=d, x=my_x, y=my_y)
    allsubsets_model = modelSelection(seed=12345, max_predictor_number=7, mode="allsubsets")
    allsubsets_model.train(training_frame=d, x=my_x, y=my_y)
    result_frame_allsubsets = allsubsets_model.result()
    numRows = result_frame_allsubsets.nrows
    best_r2_allsubsets = allsubsets_model.get_best_R2_values()
    result_frame_maxr = maxr_model.result()
    best_r2_maxr = maxr_model.get_best_R2_values()
    for ind in list(range(numRows)):
        # r2 from attributes
        best_r2_value_allsubsets = best_r2_allsubsets[ind]
        one_model_allsubsets = h2o.get_model(result_frame_allsubsets["model_id"][ind, 0])
        pred_allsubsets = one_model_allsubsets.predict(d)
        print("last element of predictor frame: {0}".format(pred_allsubsets[pred_allsubsets.nrows-1,pred_allsubsets.ncols-1]))
        assert pred_allsubsets.nrows == d.nrows, "expected dataset row: {0}, actual dataset row: " \
                                                 "{1}".format(pred_allsubsets.nrows, d.nrows)
        best_r2_value_maxr = best_r2_maxr[ind]
        one_model_maxr = h2o.get_model(result_frame_maxr["model_id"][ind, 0])
        pred_maxr = one_model_maxr.predict(d)
        pyunit_utils.compare_frames_local(pred_maxr, pred_allsubsets, prob=1, tol=1e-6) # compare allsubsets and maxr results
        # r2 from result frame
        frame_r2_allsubsets = result_frame_allsubsets["best_r2_value"][ind,0]
        # r2 from model
        model_r2_allsubsets = one_model_allsubsets.r2()
        # make sure all r2 are equal
        assert abs(best_r2_value_allsubsets-frame_r2_allsubsets) < 1e-6, "expected best r2: {0}, actual best r2: " \
                                                                   "{1}".format(best_r2_value_allsubsets, frame_r2_allsubsets)
        assert abs(frame_r2_allsubsets-model_r2_allsubsets) < 1e-6, "expected best r2: {0}, actual best r2: " \
                                                                    "{1}".format(model_r2_allsubsets, frame_r2_allsubsets)
        assert abs(best_r2_value_maxr-model_r2_allsubsets) < 1e-6, "expected best r2: {0}, maxr best r2: {1}" \
                                                             "".format(best_r2_value_maxr, model_r2_allsubsets)
        

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gaussian_result_frame_model_id)
else:
    test_gaussian_result_frame_model_id()
