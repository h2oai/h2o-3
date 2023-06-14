import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_modelselection_binomial_RID():
    '''
        In this test, I use model selection backward mode to generate GLM models with influence = dfbetas.  Next, I
        run GLM model with influence = dfbetas with the same predictor subsets as in model selection.  The rid frame
        generated from both methods should equal.
    '''
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "CAPSULE"
    my_x = ["AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON"]
    d["CAPSULE"] = d["CAPSULE"].asfactor()
    model_backward = modelSelection(seed=12345, max_predictor_number=3, mode="backward", influence="dfbetas",
                                    standardize=False, family="binomial")
    model_backward.train(training_frame=d, x=my_x, y=my_y)
    backward_rid = model_backward.get_regression_influence_diagnostics(())
    best_predictor_subsets = model_backward.get_best_model_predictors()
    for ind in range(0, len(backward_rid)):
        glm = H2OGeneralizedLinearEstimator(family="binomial", seed=1234, influence="dfbetas", standardize=False, lambda_=0.0)
        glm.train(x=best_predictor_subsets[ind], y=my_y, training_frame=d)
        glm_rid = glm.get_regression_influence_diagnostics()
        colnames = glm_rid.names
        for ind2 in range(0, len(colnames)):
            if "DFBETA" in colnames[ind2]:
                pyunit_utils.compare_frames_local(glm_rid[colnames[ind2]], backward_rid[ind][colnames[ind2]], prob=1.0, tol=1e-6)
    print("Pass test!")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_binomial_RID)
else:
    test_modelselection_binomial_RID()
