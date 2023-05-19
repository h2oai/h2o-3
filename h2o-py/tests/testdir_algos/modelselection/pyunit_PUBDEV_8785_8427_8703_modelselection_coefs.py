import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test the modelselection coef() and coef_norm() work properly.
def test_modelselection_gaussian_coefs():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    allsubsets_model = modelSelection(seed=12345, max_predictor_number=7, mode="allsubsets")
    allsubsets_model.train(training_frame=d, x=my_x, y=my_y)
    coefs_allsubsets = allsubsets_model.coef()
    coefs_norm_allsubsets = allsubsets_model.coef_norm()
    maxrsweep_model = modelSelection(seed=12345, max_predictor_number=7, mode="maxrsweep", build_glm_model=True)
    maxrsweep_model.train(training_frame=d, x=my_x, y=my_y)
    maxrsweep_model_glm = modelSelection(seed=12345, max_predictor_number=7, mode="maxrsweep")
    maxrsweep_model_glm.train(training_frame=d, x=my_x, y=my_y)
    maxrsweep_model_MM = modelSelection(seed=12345, max_predictor_number=7, mode="maxrsweep", multinode_mode=True)
    maxrsweep_model_MM.train(training_frame=d, x=my_x, y=my_y)
    maxr_model = modelSelection(seed=12345, max_predictor_number=7, mode="maxr")
    maxr_model.train(training_frame=d, x=my_x, y=my_y)
    # make sure results returned by maxr and maxrsweep are the same
    pyunit_utils.compare_frames_local(maxr_model.result()[2:4], maxrsweep_model.result()[2:4], prob=1.0, tol=1e-6)
    pyunit_utils.compare_frames_local(maxr_model.result()[2:4], maxrsweep_model_glm.result()[1:3], prob=1.0, tol=1e-6)
    pyunit_utils.compare_frames_local(maxrsweep_model_MM.result()[2:4], maxrsweep_model_glm.result()[2:4], prob=1.0, tol=1e-6)
    coefs_maxr = maxr_model.coef()
    coefs_norm_maxr = maxr_model.coef_norm()
    coefs_maxrsweep = maxrsweep_model.coef()
    coefs_norm_maxrsweep = maxrsweep_model.coef_norm()
    coefs_maxrsweep_glm = maxrsweep_model_glm.coef()
    coefs_norm_maxrsweep_glm = maxrsweep_model_glm.coef_norm()  
    coefs_marxsweep_MM = maxrsweep_model_MM.coef()
    coefs_norm_maxrsweep_MM = maxrsweep_model_MM.coef_norm()
    
    for ind in list(range(len(coefs_allsubsets))):
        one_coef_allsubsets = coefs_allsubsets[ind]
        one_coef_norm_allsubsets = coefs_norm_allsubsets[ind]
        one_coef_maxr = coefs_maxr[ind]
        one_coef_norm_maxr = coefs_norm_maxr[ind]
        one_coef_maxrsweep = coefs_maxrsweep[ind]
        one_coef_norm_maxrsweep = coefs_norm_maxrsweep[ind]
        one_coef_maxrsweep_glm = coefs_maxrsweep_glm[ind]
        one_coef_norm_maxrsweep_glm = coefs_norm_maxrsweep_glm[ind]
        one_coef_maxrsweep_MM = coefs_marxsweep_MM[ind]
        one_coef_norm_maxrsweep_MM = coefs_norm_maxrsweep_MM[ind]
        # coefficients obtained from accessing model_id, generate model and access the model coeffs
        one_model = h2o.get_model(allsubsets_model._model_json["output"]["best_model_ids"][ind]['name'])
        model_coef = one_model.coef()
        model_coef_norm = one_model.coef_norm()
        # get coefficients of individual predictor subset size
        subset_size = ind+1
        one_model_coef = allsubsets_model.coef(subset_size)
        one_model_coef_norm = allsubsets_model.coef_norm(subset_size)
        
        # check coefficient dicts are equal
        pyunit_utils.assertCoefDictEqual(one_coef_allsubsets, model_coef, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_coef_norm_allsubsets, model_coef_norm, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef, model_coef, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef_norm, model_coef_norm, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef, one_coef_maxr, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef, one_coef_maxrsweep, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef, one_coef_maxrsweep_glm, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef, one_coef_maxrsweep_MM, 1e-6)        
        pyunit_utils.assertCoefDictEqual(one_model_coef_norm, one_coef_norm_maxr, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef_norm, one_coef_norm_maxrsweep, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef_norm, one_coef_norm_maxrsweep_glm, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef_norm, one_coef_norm_maxrsweep_glm, 1e-6)
        pyunit_utils.assertCoefDictEqual(one_model_coef_norm, one_coef_norm_maxrsweep_MM, 1e-6)        
        

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_gaussian_coefs)
else:
    test_modelselection_gaussian_coefs()
