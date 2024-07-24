import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.hglm import H2OHGLMEstimator as hglm
from tests.pyunit_utils import utils_for_glm_hglm_tests

# in this test, want to check the following with standardization and with random intercept:
# 1.scoring history (both training and valid)
# 2. the model summary
# 3. Fixed effect coefficients, normal and standardized
# 4. icc
# 5. residual variance
def test_scoring_history_model_summary():
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/hglm_test/gaussian_0GC_1267R_6enum_5num_p05oise_p08T_wIntercept.gz"))
    train, valid = h2o_data.split_frame(ratios = [.8], seed = 1234)
    y = "response"
    x = h2o_data.names
    x.remove("response")
    x.remove("C1")
    random_columns = ["C2", "C3", "C10", "C20"]
    hglm_model = hglm(random_columns=random_columns, group_column = "C1", score_each_iteration=True, seed=12345,
                      em_epsilon = 0.0001, random_intercept = True, standardize = False)
    hglm_model.train(x=x, y=y, training_frame=train, validation_frame=valid)
    hglm_model2 = hglm(random_columns=random_columns, group_column = "C1", seed=12345, em_epsilon = 0.0001,
                       random_intercept = True, standardize = False) # loglikelihood calculated in training and not with scoring
    hglm_model2.train(x=x, y=y, training_frame=train, validation_frame=valid)
    # grab various metrics
    modelMetrics = hglm_model.training_model_metrics()
    scoring_history = hglm_model.scoring_history(as_data_frame=False)
    scoring_history_valid = hglm_model.scoring_history_valid(as_data_frame=False)
    model_summary = hglm_model.summary()
    modelMetrics2 = hglm_model2.training_model_metrics()
    scoring_history2 = hglm_model2.scoring_history(as_data_frame=False)
    scoring_history_valid2 = hglm_model2.scoring_history_valid(as_data_frame=False)
    model_summary2 = hglm_model2.summary()    
    coef_random_names = hglm_model.coefs_random_names()
    t_mat = hglm_model.matrix_T()
    residual_var = hglm_model.residual_variance()
    mse = hglm_model.mse()
    mse_fixed = hglm_model.mean_residual_fixed()
    mse_fixed_valid = hglm_model.mean_residual_fixed(train=False)
    icc = hglm_model.icc()
    level2_names = hglm_model.level2_names()
    # check to make sure metrics/coefficients make sense
    # residual_var = 0.05
    assert abs(residual_var-0.05) < 1.0e-3, "Expected variance: 0.05, actual: {0}.  The difference is too big."
    # residual error taking account into only fixed effect coefficients should be greater than mse, mse_valid
    assert mse < mse_fixed, "residual error with only fixed effects {0} should exceed that of mse {1} but is" \
                            " not.".format(mse_fixed, mse)
    assert mse < mse_fixed_valid, "residual error with only fixed effects from validation frames {0} should exceed that" \
                                  " of mse {1} but is not.".format(mse_fixed_valid, mse)
    # make sure level 2 values are captured correctly
    group2_value = train["C1"].unique()
    utils_for_glm_hglm_tests.compare_list_h2o_frame(level2_names, group2_value, "C1.")
    # assert icc is calculated correctly.
    assert len(t_mat) == len(coef_random_names), "expected T matrix size: {0}, actual: {1} and they are not " \
                                          "equal.".format(len(coef_random_names), len(t_mat))
    utils_for_glm_hglm_tests.check_icc_calculation(t_mat, residual_var, icc)
    # make sure contents in model summary, model history and model metrics are consistent with each other
    
    print("Done")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_scoring_history_model_summary)
else:
    test_scoring_history_model_summary()
