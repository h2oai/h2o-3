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
    myY = "GLEASON"
    myX = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    maxrglmModel = maxrglm(seed=12345, max_predictor_number=3)
    maxrglmModel.train(training_frame=d, x=myX, y=myY)
    bestR2value = maxrglmModel.get_best_R2_values()
    bestPredictorNames = maxrglmModel.get_best_model_predictors()
    
    # assert that model returned with one predictor found by maxrglm is the best
    onePredR2 = []
    for pred in myX:
        x = [pred]
        m = glm(seed=12345)
        m.train(training_frame=d,x=x,y= myY)
        onePredR2.append(m.r2())
    bestR2 = max(onePredR2)
    assert abs(bestR2-bestR2value[0]) < 1e-6, "expected best r2: {0}, actual best r2:{1}.  They are different." \
                                              "".format(bestR2value[0], bestR2)
    print("Best one predictor model uses predictor: {0}".format(bestPredictorNames[0]))
    
    myX3 = [["AGE","RACE","CAPSULE"], ["AGE","RACE","DCAPS"], ["AGE","RACE","PSA"], ["AGE","RACE","VOL"], 
            ["AGE","RACE","DPROS"], ["AGE","CAPSULE","DCAPS"], ["AGE","CAPSULE","PSA"], ["AGE","CAPSULE","VOL"],
            ["AGE","CAPSULE","DPROS"],["AGE","DCAPS","PSA"],["AGE","DCAPS","PSA"],["AGE","DCAPS","VOL"],
            ["AGE","DCAPS","DPROS"],["AGE","PSA","VOL"],["AGE","PSA","VOL"],["AGE","PSA","DPROS"],
            ["AGE","VOL","DPROS"],["RACE","CAPSULE","DCAPS"], ["RACE","CAPSULE","PSA"], ["RACE","CAPSULE","VOL"], 
            ["RACE","CAPSULE","DPROS"], ["RACE","DCAPS","PSA"],["RACE","DCAPS","VOL"],["RACE","DCAPS","DPROS"],
            ["RACE","PSA","VOL"],["RACE","PSA","DPROS"],["RACE","VOL","DPROS"],["CAPSULE","DCAPS","PSA"],
            ["CAPSULE","DCAPS","VOL"],["CAPSULE","DCAPS","DPROS"], ["DCAPS","PSA","VOL"],["DCAPS","PSA","DPROS"],
            ["DCAPS","VOL","DPROS"],["PSA","VOL","DPROS"]]
    twoPredR2 = []
    for pred2 in myX3:
        x = pred2
        m = glm(seed=12345)
        m.train(training_frame=d, x=x, y=myY)
        twoPredR2.append(m.r2())
    bestR2TwoPred = max(twoPredR2)
    assert abs(bestR2TwoPred-bestR2value[2]) < 1e-6, "expected best r2: {0}, actual best r2:{1}.  They are different." \
                                                     "".format(bestR2value[2], bestR2TwoPred)
    print("Best three predictors model uses predictors: {0}".format(bestPredictorNames[2]))


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrglm_gaussian)
else:
    test_maxrglm_gaussian()
