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
    my_x = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14",
           "C15", "C16", "C17", "C18", "C19", "C20"]
    factor_x = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for x in factor_x:
        d[x] = d[x].asfactor()
    frames = d.split_frame(ratios=[0.8],seed=12345)
    train = frames[0]
    test = frames[1]
    maxrglm_model = maxrglm(seed=12345, max_predictor_number=3)
    maxrglm_model.train(training_frame=train, x=my_x, y=my_y)
    best_r2_value = maxrglm_model.get_best_R2_values()
    best_predictor_names = maxrglm_model.get_best_model_predictors()
    maxrglm_model_v = maxrglm(seed=12345, max_predictor_number=3)
    maxrglm_model_v.train(training_frame=train, validation_frame=test, x=my_x, y=my_y)
    best_r2_value_v = maxrglm_model_v.get_best_R2_values()
    best_predictor_names_v = maxrglm_model.get_best_model_predictors()
    
    # R2 values are different between the two models
    numSet = len(best_r2_value)
    for index in range(numSet):
        best_predictor = best_predictor_names[index]
        best_predictor_v = best_predictor_names_v[index]
        best_r2 = best_r2_value[index]
        best_r2_v = best_r2_value_v[index]
        if best_predictor == best_predictor_v:
            assert not(best_r2 == best_r2_v), "R2 values should not equal"
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrglm_validation)
else:
    test_maxrglm_validation()
