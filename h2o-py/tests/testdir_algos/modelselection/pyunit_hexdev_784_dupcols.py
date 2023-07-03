import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

# In this test, the training data contains duplicated columns that will need to be removed.  Just want to check
# and make sure that the multinod_mode=True and False will return the same results.
def test_maxrsweep_dup_cols():
    train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/model_selection/wideDupCols.csv")
    response="response"
    predictors = train.names
    predictors.remove(response)
    npred = 20
    maxrsweep_model_glm = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True, 
                                                 build_glm_model=True)
    maxrsweep_model_glm.train(x=predictors, y=response, training_frame=train)
    maxrsweep_model_glm_MM = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True,
                                                     build_glm_model=True, multinode_mode=True)
    maxrsweep_model_glm_MM.train(x=predictors, y=response, training_frame=train)
    maxrsweep_model = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True)
    maxrsweep_model.train(x=predictors, y=response, training_frame=train)
    maxrsweep_model_MM = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True, 
                                                        multinode_mode=True)
    maxrsweep_model_MM.train(x=predictors, y=response, training_frame=train)
    
    maxrsweep_glm_coeffs = maxrsweep_model_glm.coef()
    maxrsweep_glm_coeffs_MM = maxrsweep_model_glm_MM.coef()
    maxrsweep_coeffs = maxrsweep_model.coef()
    maxrsweep_coeffs_MM = maxrsweep_model_MM.coef()

    maxrsweep_glm_coeffs_norm = maxrsweep_model_glm.coef_norm()
    maxrsweep_glm_coeffs_MM_norm = maxrsweep_model_glm_MM.coef_norm()
    maxrsweep_coeffs_norm = maxrsweep_model.coef_norm()
    maxrsweep_coeffs_MM_norm = maxrsweep_model_MM.coef_norm()

    maxrsweep_best_model_predictors = maxrsweep_model.get_best_model_predictors()
    maxrsweep_best_model_predictors_MM = maxrsweep_model_MM.get_best_model_predictors()
    maxrsweep_best_model_predictors_glm = maxrsweep_model_glm.get_best_model_predictors()
    maxrsweep_best_model_predictors_glm_MM = maxrsweep_model_glm_MM.get_best_model_predictors()

# make sure all models produce the same set of coefficients since they are building the same predictor subsets
    for ind in range(0, len(maxrsweep_coeffs)):
        pyunit_utils.assertCoefDictEqual(maxrsweep_coeffs[ind], maxrsweep_coeffs_MM[ind], 1e-6)
        pyunit_utils.assertCoefDictEqual(maxrsweep_coeffs[ind], maxrsweep_glm_coeffs_MM[ind], 1e-6)
        pyunit_utils.assertCoefDictEqual(maxrsweep_coeffs[ind], maxrsweep_glm_coeffs[ind], 1e-6)
        pyunit_utils.assertCoefDictEqual(maxrsweep_coeffs_norm[ind], maxrsweep_coeffs_MM_norm[ind], 1e-6)
        pyunit_utils.assertCoefDictEqual(maxrsweep_coeffs_norm[ind], maxrsweep_glm_coeffs_MM_norm[ind], 1e-6)
        pyunit_utils.assertCoefDictEqual(maxrsweep_coeffs_norm[ind], maxrsweep_glm_coeffs_norm[ind], 1e-6)
        maxrsweep_best_model_predictors[ind].sort()
        maxrsweep_best_model_predictors_MM[ind].sort()
        maxrsweep_best_model_predictors_glm[ind].sort()
        maxrsweep_best_model_predictors_glm_MM[ind].sort()
        assert maxrsweep_best_model_predictors[ind] == maxrsweep_best_model_predictors_MM[ind], \
            "normal vs multinode mode: Expected predictor subset: {0}, actual predictor subset: {1}".format(maxrsweep_best_model_predictors[ind],
                                                                                  maxrsweep_best_model_predictors_MM[ind])
        assert maxrsweep_best_model_predictors[ind] == maxrsweep_best_model_predictors_glm[ind], \
            "normal vs build glm: Expected predictor subset: {0}, actual predictor subset: {1}".format(maxrsweep_best_model_predictors_glm[ind],
                                                                                  maxrsweep_best_model_predictors_MM[ind])
        assert maxrsweep_best_model_predictors[ind] == maxrsweep_best_model_predictors_glm_MM[ind], \
            "normal vs build glm multinode mode: Expected predictor subset: {0}, actual predictor subset: {1}".format(maxrsweep_best_model_predictors_glm_MM[ind],
                                                                                  maxrsweep_best_model_predictors_MM[ind])
        print("comparing results for predictor subset size {0}".format(ind+1))

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrsweep_dup_cols)
else:
    test_maxrsweep_dup_cols()
