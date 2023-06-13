import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

def test_maxrsweep_NPE():
    #train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv")
    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv"))
    response="response"
    predictors = train.names
    predictors.remove(response)
    npred = 100
    maxrsweep_model = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True, 
                                                 build_glm_model=True)
    maxrsweep_model.train(x=predictors, y=response, training_frame=train)
    maxrsweep_coeffs = maxrsweep_model.coef()
    maxrsweep_coeffs_norm = maxrsweep_model.coef_norm()
    maxrsweep_best_model_predictors = maxrsweep_model.get_best_model_predictors()
    maxrsweep_model_glm = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True)
    maxrsweep_model_glm.train(x=predictors, y=response, training_frame=train)
    maxrsweepGLM_coeffs = maxrsweep_model_glm.coef()
    maxrsweepGLM_coeffs_norm = maxrsweep_model_glm.coef_norm()
    maxrsweepGLM_best_model_predictors = maxrsweep_model_glm.get_best_model_predictors()

    maxrsweep_model_MM = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True,
                                                     multinode_mode=True)
    maxrsweep_model_MM.train(x=predictors, y=response, training_frame=train)
    maxrsweep_coeffs_MM = maxrsweep_model_MM.coef()
    maxrsweep_coeffs_norm_MM = maxrsweep_model_MM.coef_norm()
    maxrsweep_best_model_predictors_MM = maxrsweep_model_MM.get_best_model_predictors()

    for ind in range(0, len(maxrsweep_coeffs)):
        pyunit_utils.assertCoefDictEqual(maxrsweep_coeffs[ind], maxrsweepGLM_coeffs[ind], 1e-6)
        pyunit_utils.assertCoefDictEqual(maxrsweep_coeffs[ind], maxrsweep_coeffs_MM[ind], 1e-6)
        pyunit_utils.assertCoefDictEqual(maxrsweep_coeffs_norm[ind], maxrsweepGLM_coeffs_norm[ind], 1e-6)
        pyunit_utils.assertCoefDictEqual(maxrsweep_coeffs_norm[ind], maxrsweep_coeffs_norm_MM[ind], 1e-6)
        maxrsweep_best_model_predictors[ind].sort()
        maxrsweepGLM_best_model_predictors[ind].sort()
        maxrsweep_best_model_predictors_MM[ind].sort()
        assert maxrsweep_best_model_predictors[ind] == maxrsweepGLM_best_model_predictors[ind], \
            "Expected predictor subset: {0}, actual predictor subset: {1}".format(maxrsweep_best_model_predictors[ind],
                                                                                  maxrsweepGLM_best_model_predictors[ind])
        assert maxrsweep_best_model_predictors[ind] == maxrsweep_best_model_predictors_MM[ind], \
            "Expected predictor subset: {0}, actual predictor subset: {1}".format(maxrsweep_best_model_predictors[ind],
                                                                                  maxrsweep_best_model_predictors_MM[ind])

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrsweep_NPE)
else:
    test_maxrsweep_NPE()
