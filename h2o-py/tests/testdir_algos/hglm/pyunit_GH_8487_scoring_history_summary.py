import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.hglm import H2OHGLMEstimator as hglm

# in this test, want to check the following:
# 1.scoring history (both training and valid)
# 2. the model summary
# 3. Fixed effect coefficients, normal and standardized
# 4. icc
# 5. residual variance
def test_scoring_history_model_summary():
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/hglm_test/gaussian_0GC_678R_6enum_5num_p05oise_p08T_wIntercept_standardize.gz"))
    train, valid = h2o_data.split_frame(ratios = [.8], seed = 1234)
    y = "response"
    x = h2o_data.names
    x.remove("response")
    x.remove("C1")
    random_columns = ["C10","C20","C30"]
    hglm_model = hglm(random_columns=random_columns, group_column = "C1", score_each_iteration=True, seed=12345,
                      em_epsilon = 0.1)
    hglm_model.train(x=x, y=y, training_frame=train, validation_frame=valid)
    modelMetrics = hglm_model.training_model_metrics()
    scoring_history = hglm_model.scoring_history(as_data_frame=False)
    scoring_history_valid = hglm_model.scoring_history_valid(as_data_frame=False)
    model_summary = hglm_model.summary()
    coef = hglm_model.coef()
    coef_norm = hglm_model.coef_norm()
    coef_names = hglm_model.coef_names()
    coef_random = hglm_model.coefs_random()
    t_mat = hglm_model.matrix_T()
    residual_var = hglm_model.residual_variance()
    mse = hglm_model.mse()
    icc = hglm_model.icc()
    
    print("Done")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_scoring_history_model_summary)
else:
    test_scoring_history_model_summary()
