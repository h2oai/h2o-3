from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test modelselection works with validation dataset
def test_modelselection_validation():
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
    allsubsets_model = modelSelection(seed=12345, max_predictor_number=3, mode="allsubsets")
    allsubsets_model.train(training_frame=train, x=my_x, y=my_y)
    best_r2_allsubsets = allsubsets_model.get_best_R2_values()
    best_predictor_allsubsets = allsubsets_model.get_best_model_predictors()
    allsubsets_model_v = modelSelection(seed=12345, max_predictor_number=3, mode="allsubsets")
    allsubsets_model_v.train(training_frame=train, validation_frame=test, x=my_x, y=my_y)
    best_r2_allsubsets_v = allsubsets_model_v.get_best_R2_values()
    best_predictor_allsubsets_v = allsubsets_model.get_best_model_predictors()
    
    maxr_model_v = modelSelection(seed=12345, max_predictor_number=3, mode="maxr")
    maxr_model_v.train(training_frame=train, validation_frame=test, x=my_x, y=my_y)
    best_r2_maxr_v = maxr_model_v.get_best_R2_values()
    best_predictor_maxr_v = maxr_model_v.get_best_model_predictors()
    
    # R2 values are different between the two models
    numSet = len(best_r2_allsubsets)
    for index in range(numSet):
        one_best_predictor_allsubsets = best_predictor_allsubsets[index]
        one_best_predictor_v_allsubsets = best_predictor_allsubsets_v[index]
        one_best_r2_allsubsets = best_r2_allsubsets[index]
        one_best_r2_v_allsubsets = best_r2_allsubsets_v[index]
        best_r2_v_maxr = best_r2_maxr_v[index]
        if one_best_predictor_allsubsets == one_best_predictor_v_allsubsets:
            assert not(one_best_r2_allsubsets == one_best_r2_v_allsubsets), "R2 values should not equal"
            assert abs(one_best_r2_v_allsubsets-best_r2_v_maxr) < 1e-6, "allsubset best R2: {0}, maxr best R2: {1}.  They " \
                                                                    "are different.".format(one_best_r2_v_allsubsets, 
                                                                                            best_r2_v_maxr)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_validation)
else:
    test_modelselection_validation()
