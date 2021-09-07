from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
import math
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
from h2o.estimators.maxrglm import H2OMaxRGLMEstimator as maxrglm

# test maxrglm algorithm for regression only.  In particular, we are interested in making sure the models returned
# have the best R2 square value for one predictor and three predictors
def test_maxrglm_gaussian():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    maxrglm_model = maxrglm(seed=12345, max_predictor_number=3)
    maxrglm_model.train(training_frame=d, x=my_x, y=my_y)
    best_r2_value = maxrglm_model.get_best_R2_values()
    best_predictor_names = maxrglm_model.get_best_model_predictors()
    
    # assert that model returned with one predictor found by maxrglm is the best
    one_pred_r2 = []
    for pred in my_x:
        x = [pred]
        m = glm(seed=12345)
        m.train(training_frame=d,x=x,y= my_y)
        one_pred_r2.append(m.r2())
    best_r2 = max(one_pred_r2)
    assert abs(best_r2-best_r2_value[0]) < 1e-6, "expected best r2: {0}, actual best r2:{1}.  They are different." \
                                              "".format(best_r2_value[0], best_r2)
    print("Best one predictor model uses predictor: {0}".format(best_predictor_names[0]))
    
    my_x3 = [["AGE","RACE","CAPSULE"], ["AGE","RACE","DCAPS"], ["AGE","RACE","PSA"], ["AGE","RACE","VOL"], 
            ["AGE","RACE","DPROS"], ["AGE","CAPSULE","DCAPS"], ["AGE","CAPSULE","PSA"], ["AGE","CAPSULE","VOL"],
            ["AGE","CAPSULE","DPROS"],["AGE","DCAPS","PSA"],["AGE","DCAPS","PSA"],["AGE","DCAPS","VOL"],
            ["AGE","DCAPS","DPROS"],["AGE","PSA","VOL"],["AGE","PSA","VOL"],["AGE","PSA","DPROS"],
            ["AGE","VOL","DPROS"],["RACE","CAPSULE","DCAPS"], ["RACE","CAPSULE","PSA"], ["RACE","CAPSULE","VOL"], 
            ["RACE","CAPSULE","DPROS"], ["RACE","DCAPS","PSA"],["RACE","DCAPS","VOL"],["RACE","DCAPS","DPROS"],
            ["RACE","PSA","VOL"],["RACE","PSA","DPROS"],["RACE","VOL","DPROS"],["CAPSULE","DCAPS","PSA"],
            ["CAPSULE","DCAPS","VOL"],["CAPSULE","DCAPS","DPROS"], ["DCAPS","PSA","VOL"],["DCAPS","PSA","DPROS"],
            ["DCAPS","VOL","DPROS"],["PSA","VOL","DPROS"]]
    two_pred_r2 = []
    for pred2 in my_x3:
        x = pred2
        m = glm(seed=12345)
        m.train(training_frame=d, x=x, y=my_y)
        two_pred_r2.append(m.r2())
    best_r2_two_pred = max(two_pred_r2)
    assert abs(best_r2_two_pred-best_r2_value[2]) < 1e-6, "expected best r2: {0}, actual best r2:{1}.  They are different." \
                                                     "".format(best_r2_value[2], best_r2_two_pred)
    print("Best three predictors model uses predictors: {0}".format(best_predictor_names[2]))


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrglm_gaussian)
else:
    test_maxrglm_gaussian()
