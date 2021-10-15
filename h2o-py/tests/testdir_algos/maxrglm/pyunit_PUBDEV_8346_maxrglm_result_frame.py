from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import math
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
from h2o.estimators.maxrglm import H2OMaxRGLMEstimator as maxrglm

# test maxrglm algorithm for regression only.  Make sure the result frame contains the correct information.  Make
# sure that we can instantiate the best model from model ID, perform scoring with it.
def test_maxrglm_gaussian():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    maxrglm_model = maxrglm(seed=12345, max_predictor_number=7)
    maxrglm_model.train(training_frame=d, x=my_x, y=my_y)
    resultFrame = maxrglm_model.result()
    numRows = resultFrame.nrows
    best_r2_value = maxrglm_model.get_best_R2_values()
    for ind in list(range(numRows)):
        # r2 from attributes
        best_r2 = best_r2_value[ind]
        one_model = h2o.get_model(resultFrame["model_id"][ind, 0])
        pred = one_model.predict(d)
        print("last element of predictor frame: {0}".format(pred[pred.nrows-1,pred.ncols-1]))
        assert pred.nrows == d.nrows, "expected dataset row: {0}, actual dataset row: {1}".format(pred.nrows, d.nrows)
        # r2 from result frame
        frame_r2 = resultFrame["best_r2_value"][ind,0]
        # r2 from model
        model_r2 = one_model.r2()
        # make sure all r2 are equal
        assert abs(best_r2-frame_r2) < 1e-6, "expected best r2: {0}, actual best r2: {1}".format(best_r2, frame_r2)
        assert abs(frame_r2-model_r2) < 1e-6, "expected best r2: {0}, actual best r2: {1}".format(model_r2, frame_r2)
        

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrglm_gaussian)
else:
    test_maxrglm_gaussian()
