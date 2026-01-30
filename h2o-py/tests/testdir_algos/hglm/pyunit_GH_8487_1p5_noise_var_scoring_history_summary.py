import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.hglm import H2OHGLMEstimator as hglm
from tests.pyunit_utils import utils_for_glm_hglm_tests

# in this test, want to check the following with random intercept:
# 1.scoring history (both training and valid)
# 2. the model summary
# 3. Fixed effect coefficients, normal and standardized
# 4. icc
# 5. residual variance
def test_scoring_history_model_summary():
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/hglm_test/gaussian_0GC_123R_6enum_5num_1p5oise_p08T_woIntercept_standardize.gz"))
    train, valid = h2o_data.split_frame(ratios = [.8], seed = 1234)
    y = "response"
    x = h2o_data.names
    x.remove("response")
    x.remove("C1")
    random_columns = ["C2", "C3", "C4"]
    hglm_model = hglm(random_columns=random_columns, group_column = "C1", score_each_iteration=True, seed=12345,
                      max_iterations = 20, random_intercept = False)
    hglm_model.train(x=x, y=y, training_frame=train, validation_frame=valid)
    # grab various metrics
    model_metrics = hglm_model.training_model_metrics()
    scoring_history = hglm_model.scoring_history(as_data_frame=False)
    scoring_history_valid = hglm_model.scoring_history_valid(as_data_frame=False)
    model_summary = hglm_model.summary()
    coef_random_names = hglm_model.coefs_random_names()
    t_mat = hglm_model.matrix_T()
    residual_var = hglm_model.residual_variance()
    mse = hglm_model.mse()
    mse_fixed = hglm_model.mean_residual_fixed()
    mse_fixed_valid = hglm_model.mean_residual_fixed(train=False)
    icc = hglm_model.icc()
    level2_names = hglm_model.level_2_names()
    
    # check to make sure metrics/coefficients make sense
    residual_var_true = 1.5
    assert abs(residual_var-residual_var_true) < 0.05, \
        "Expected variance: {1}, actual: {0}.  The difference is too big.".format(residual_var, residual_var_true)
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
    # check model summary and model metrics if contain the same information should equal to each other
    model_iterations = model_metrics["iterations"]
    assert model_iterations == model_summary.cell_values[0][1], \
        "model metrics iterations {0} should equal model_summary iterations {1}".format(model_iterations, model_summary.cell_values[0][1])
    last_mse = model_metrics["MSE"]
    assert abs(last_mse - model_summary.cell_values[0][3]) < 1e-6, \
        "model metrics MSE {0} should equal to model summary MSE {1}.".format(last_mse, model_summary.cell_values[0][3])
    last_llg = model_metrics["log_likelihood"]
    assert abs(last_llg - model_summary.cell_values[0][2]) < 1e-6,\
        "model metrics llg {0} should equal to model summary llg {1}.".format(last_llg, model_summary.cell_values[0][2])
    # check scoring history last entry with model metric values
    assert len(scoring_history.cell_values) == model_iterations, \
        "length of scoring history {0} should equal to number of model iterations {1}".format(len(scoring_history.cell_values), model_iterations)
    last_sc_index = model_iterations-1
    assert abs(scoring_history.cell_values[last_sc_index][3] - last_llg) < 1e-6, \
        "last scoring history llg {0} should equal to model metrics llg {1}".format(scoring_history.cell_values[last_sc_index][3], last_llg)
    assert abs(scoring_history.cell_values[last_sc_index][4] - last_mse) < 1e-6, \
        "last scoring history MSE {0} should equal to model metrics MSE {1}.".format(scoring_history.cell_values[last_sc_index][4], last_mse)
    # check and make sure the llg from training and validation frame should be increasing in values
    # this is only true when the true residual variance is high.  For low true residual variance, it is only
    # true for the last few iterations when the residual variance estimate is close to the true residual variance
    if (residual_var_true > 2):
        for ind in list(range(1, model_iterations)):
            p_ind = ind-1
            assert scoring_history.cell_values[p_ind][3] <= scoring_history.cell_values[ind][3], \
                "training llg {0} from iteration {1} should be smaller than training llg {2} from iteration " \
                "{3}".format(scoring_history.cell_values[p_ind][3], p_ind, scoring_history.cell_values[ind][3], ind)
            assert scoring_history_valid.cell_values[p_ind][3] <= scoring_history_valid.cell_values[ind][3], \
                "validation llg {0} from iteration {1} should be smaller than validation llg {2} from iteration " \
                "{3}".format(scoring_history_valid.cell_values[p_ind][3], p_ind, scoring_history_valid.cell_values[ind][3], ind)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_scoring_history_model_summary)
else:
    test_scoring_history_model_summary()
