from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.maxrglm import H2OMaxRGLMEstimator as maxrglm

# test maxrglm works with cross-validation
def test_maxrglm_cross_validation():

    d = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    my_y = "C21"
    my_x = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C11", "C12", "C13", "C14", "C15", "C16",
           "C17", "C18", "C19", "C20"]
    factorX = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for x in factorX:
        d[x] = d[x].asfactor()
    n_folds = 3

    maxrglm_model_r = maxrglm(seed=12345, max_predictor_number=3, nfolds=n_folds, fold_assignment="random")
    maxrglm_model_r.train(training_frame=d, x=my_x, y=my_y)
    best_r_value_r = maxrglm_model_r.get_best_R2_values()

    maxrglm_model_a = maxrglm(seed=12345, max_predictor_number=3, nfolds=n_folds, fold_assignment="auto")
    maxrglm_model_a.train(training_frame=d, x=my_x, y=my_y)
    best_r2_value_a = maxrglm_model_a.get_best_R2_values()
     
     # both models should provide same best R2 values
    pyunit_utils.equal_two_arrays(best_r_value_r, best_r2_value_a, eps=1e-6)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrglm_cross_validation)
else:
    test_maxrglm_cross_validation()
