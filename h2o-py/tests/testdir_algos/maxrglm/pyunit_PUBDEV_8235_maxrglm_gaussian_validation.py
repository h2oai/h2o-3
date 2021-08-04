from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.maxrglm import H2OMaxRGLMEstimator as maxrglm

# test maxrglm works with validation dataset
def test_maxrglm_validation():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    my_y = "C21"
    myX = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14",
           "C15", "C16", "C17", "C18", "C19", "C20"]
    factorX = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for x in factorX:
        d[x] = d[x].asfactor()
    frames = d.split_frame(ratios=[0.8],seed=12345)
    train = frames[0]
    test = frames[1]
    maxrglmModel = maxrglm(seed=12345, max_predictor_number=3)
    maxrglmModel.train(training_frame=train, x=myX, y=myY)
    bestR2value = maxrglmModel.get_best_R2_values()
    bestPredictorNames = maxrglmModel.get_best_model_predictors()
    maxrglmModelV = maxrglm(seed=12345, max_predictor_number=3)
    maxrglmModelV.train(training_frame=train, validation_frame=test, x=myX, y=myY)
    bestR2valueV = maxrglmModelV.get_best_R2_values()
    bestPredictorNamesV = maxrglmModel.get_best_model_predictors()
    
    # R2 values are different between the two models
    numSet = len(bestR2value)
    for index in range(numSet):
        bestPredictor = bestPredictorNames[index]
        bestPredictorV = bestPredictorNamesV[index]
        bestR2 = bestR2value[index]
        bestR2V = bestR2valueV[index]
        if bestPredictor == bestPredictorV:
            assert not(bestR2 == bestR2V), "R2 values should not equal"
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrglm_validation)
else:
    test_maxrglm_validation()
