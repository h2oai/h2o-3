import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.hglm import H2OHGLMEstimator as hglm
from tests.pyunit_utils import utils_for_glm_hglm_tests

# Test that model built with random intercept work properly
def test_model_with_random_intercept_only():
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/hglm_test/gaussian_0GC_allRC_2enum2numeric_3noise_p08T_wIntercept_standardize.gz"))
    train, valid = h2o_data.split_frame(ratios = [.8], seed = 1234)
    y = "response"
    x = h2o_data.names
    x.remove("response")
    x.remove("C1")
    random_columns = ["C2", "C3", "C10", "C20"]
    hglm_model = hglm(random_columns=random_columns, group_column = "C1", score_each_iteration=True, seed=12345,
                      random_intercept = True, max_iterations=10)
    hglm_model.train(x=x, y=y, training_frame=train, validation_frame=valid)
    hglm_model_random_intercept = hglm(group_column = "C1", score_each_iteration=True, seed=12345, 
                                       random_intercept = True, max_iterations=10)
    hglm_model_random_intercept.train(x=x, y=y, training_frame=train, validation_frame=valid)
    mse = hglm_model.mse()
    mse_random_intercept = hglm_model_random_intercept.mse()

    # check to make sure metrics/coefficients make sense
    assert mse < mse_random_intercept, "MSE {0} with random_columns should be lower than model built with random " \
                                       "intercept only MSE {1}".format(mse, mse_random_intercept)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_model_with_random_intercept_only)
else:
    test_model_with_random_intercept_only()
