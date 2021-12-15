from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test modelselection works with cross-validation
def test_modelseletion_modelselection_cross_validation():

    d = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    my_y = "C21"
    my_x = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16",
           "C17", "C18", "C19", "C20"]
    factorX = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for x in factorX:
        d[x] = d[x].asfactor()
    n_folds = 3

    maxr_model_r = modelSelection(seed=12345, max_predictor_number=3, nfolds=n_folds, fold_assignment="random", 
                                     mode="maxr")
    maxr_model_r.train(training_frame=d, x=my_x, y=my_y)
    best_r2_maxr_r = maxr_model_r.get_best_R2_values()

    maxrglm_model_a = modelSelection(seed=12345, max_predictor_number=3, nfolds=n_folds, fold_assignment="auto", 
                                     mode="maxr")
    maxrglm_model_a.train(training_frame=d, x=my_x, y=my_y)
    best_r2_maxr_a = maxrglm_model_a.get_best_R2_values()
     
     # both models should provide same best R2 values
    pyunit_utils.equal_two_arrays(best_r2_maxr_r, best_r2_maxr_a, eps=1e-6)

    allsubsets_model_r = modelSelection(seed=12345, max_predictor_number=3, nfolds=n_folds, fold_assignment="random",
                                     mode="allsubsets")
    allsubsets_model_r.train(training_frame=d, x=my_x, y=my_y)
    best_r2_allsubsets_r = allsubsets_model_r.get_best_R2_values()
    pyunit_utils.equal_two_arrays(best_r2_allsubsets_r, best_r2_maxr_r, eps=1e-6) # maxr and allsubsets r2 should equal

    allsubsets_model_a = modelSelection(seed=12345, max_predictor_number=3, nfolds=n_folds, fold_assignment="auto",
                                 mode="allsubsets")
    allsubsets_model_a.train(training_frame=d, x=my_x, y=my_y)
    best_r2_allsubsets_a = allsubsets_model_a.get_best_R2_values()
    pyunit_utils.equal_two_arrays(best_r2_allsubsets_a, best_r2_maxr_a, eps=1e-6) # maxr and allsubsets r2 should equal

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelseletion_modelselection_cross_validation)
else:
    test_modelseletion_modelselection_cross_validation()
