from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.maxrglm import H2OMaxRGLMEstimator as maxrglm

# test the maxrglm coef() and coef_norm() work properly.
def test_maxrglm_gaussian_coefs():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    maxrglm_model = maxrglm(seed=12345, max_predictor_number=7)
    maxrglm_model.train(training_frame=d, x=my_x, y=my_y)
    coefs = maxrglm_model.coef()
    coefs_norm = maxrglm_model.coef_norm()
    for ind in list(range(len(coefs))):
        one_coef = coefs[ind]
        one_coef_norm = coefs_norm[ind]
        # coefficients obtained from accessing model_id, generate model and access the model coeffs
        one_model = h2o.get_model(maxrglm_model._model_json["output"]["best_model_ids"][ind]['name'])
        model_coef = one_model.coef()
        model_coef_norm = one_model.coef_norm()
        # get coefficients of individual predictor subset size
        subset_size = ind+1
        one_model_coef = maxrglm_model.coef(subset_size)
        one_model_coef_norm = maxrglm_model.coef_norm(subset_size)
        
        # check coefficient dicts are equal
        pyunit_utils.assertCoefDictEqual(one_coef, model_coef, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_coef_norm, model_coef_norm, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef, model_coef, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef_norm, model_coef_norm, 1e-6)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrglm_gaussian_coefs)
else:
    test_maxrglm_gaussian_coefs()
