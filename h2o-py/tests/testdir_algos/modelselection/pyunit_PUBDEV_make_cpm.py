import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

def test_maxrsweep_replacement():
    train = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    response="C21"
    predictors = train.names
    predictors.remove(response)
    npred = 5
    x = ["C11","C12","C13","C14","C15","C16","C17","C18","C19","C20"]
    maxrsweep_model = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True, 
                                                 build_glm_model=False, standardize=True)
    maxrsweep_model.train(x=x, y=response, training_frame=train)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrsweep_replacement)
else:
    test_maxrsweep_replacement()
