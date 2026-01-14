import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.hglm import H2OHGLMEstimator as hglm
from tests.pyunit_utils import utils_for_glm_hglm_tests

# in this test, want to check to make sure we are getting our coefficients
# 1. Fixed effect coefficients;
# 2. Random effect coefficients.
def test_scoring_history_model_summary():
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/hglm_test/gaussian_0GC_allRC_2enum2numeric_3noise_p08T_wIntercept_standardize.gz"))
    train, valid = h2o_data.split_frame(ratios = [.8], seed = 1234)
    y = "response"
    x = h2o_data.names
    x.remove("response")
    x.remove("C1")
    random_columns = ["C2", "C3", "C10", "C20"]
    hglm_model = hglm(random_columns=random_columns, group_column = "C1", score_each_iteration=True, seed=12345, 
                      max_iterations=10)
    hglm_model.train(x=x, y=y, training_frame=train, validation_frame=valid)
    # grab various metrics
    coef = hglm_model.coef()
    coef_random = hglm_model.coefs_random()
    coef_random_names = hglm_model.coefs_random_names()
    residual_var = hglm_model.residual_variance()
    mse = hglm_model.mse()
    mse_fixed = hglm_model.mean_residual_fixed()
    mse_fixed_valid = hglm_model.mean_residual_fixed(train=False)
    level2_names = hglm_model.level_2_names()
    # check to make sure metrics/coefficients make sense

    true_residual_var = 3.0
    assert abs(residual_var-true_residual_var) < 5.0e-2, \
        "Expected variance: {1}, actual: {0}.  The difference is too big.".format(residual_var, true_residual_var)
    # residual error taking account into only fixed effect coefficients should be greater than mse, mse_valid
    assert mse < mse_fixed, "residual error with only fixed effects {0} should exceed that of mse {1} but is" \
                            " not.".format(mse_fixed, mse)
    assert mse < mse_fixed_valid, "residual error with only fixed effects from validation frames {0} should exceed that" \
                                  " of mse {1} but is not.".format(mse_fixed_valid, mse)
    assert len(coef) == len(coef_random_names), "fixed coefficient length {0} should equal to random coefficient names" \
                                                " length: {1}".format(len(coef), len(coef_random_names))
    assert len(level2_names) == len(coef_random), \
        "expected random coefficient length: {0}, actual random coefficient names length " \
        "{1}".format(len(level2_names),len(coef_random)) 

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_scoring_history_model_summary)
else:
    test_scoring_history_model_summary()
