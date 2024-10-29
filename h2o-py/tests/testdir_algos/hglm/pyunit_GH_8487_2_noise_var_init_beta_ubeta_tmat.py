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
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/hglm_test/gaussian_0GC_123R_all5Numeric_p2noise_p08T_woIntercept_standardize.gz"))
    beta = [1.5606284972932365, -0.0002347762275008978, -0.007899880335654788, 0.0018421903682971376, 
            0.6654323495890934, -0.6544609203736372] 
    ubeta = [[-0.9319187693195115, 0.6070501821727673, 0.8394540491750797],
             [-1.3823145230494698, 0.21486874352840676, 0.8366860141888742],
             [-0.552534049777237, 0.24577758770128783, 0.8172622402154629],
             [-0.7632283839126288, 0.3662979940622124, 0.8382611342477616],
             [-0.7660574987463035, 0.5278044590884986, 0.8421686869476276],
             [-1.2704526364630178, 0.3882261064670864, 0.8626801006264753],
             [-1.2615857701992563, 0.39167873788423885, 0.8448421359246485],
             [-1.1863349889243804, 0.4802231651611951, 0.852783164270973]]
    ubeta_init = h2o.H2OFrame(ubeta)
    t_mat = [[1.1086713375915982, -0.40493787563311834, -0.8561132576680854],
             [-0.40493787563311834, 0.17812207973788066, 0.33964543424526844],
             [-0.8561132576680854, 0.33964543424526844, 0.709024192121366]]
    t_mat_init = h2o.H2OFrame(t_mat)
    y = "response"
    x = h2o_data.names
    x.remove("response")
    x.remove("C1")
    random_columns = ["C2", "C3", "C4"]
    # hglm_model = hglm(random_columns=random_columns, group_column = "C1", score_each_iteration=True, seed=12345,
    #                   max_iterations = 20, random_intercept = False)
    hglm_model = hglm(random_columns = random_columns, group_column = "C1", seed = 12345, max_iterations = 0,
                      random_intercept = False, initial_fixed_effects = beta, initial_random_effects = ubeta_init,
                      initial_t_matrix = t_mat_init)
    hglm_model.train(x=x, y=y, training_frame=h2o_data)
    # check and make sure the fixed effect coeffs, random effect coeffs and matrix T from model should equal to the 
    # original initial values since we set max_iterations = 0
    beta_model = hglm_model.coef()
    # compare intital beta
    for index in range(4):
        assert abs(beta[index]-beta_model[x[index]]) < 1e-6, \
            "fixed coefficients for {0} from model: {1}, from initialization: {2} should be the same but is " \
            "not.".format(x[index], beta_model[x[index]], beta[index])
    ubeta_model = hglm_model.coefs_random()
    level_2_names = hglm_model.level_2_names()
    for index in range(len(level_2_names)):
        pyunit_utils.equal_two_arrays(ubeta[index], ubeta_model[level_2_names[index]])
    t_mat_model = hglm_model.matrix_T()
    for index in range(len(t_mat_model)):
        pyunit_utils.equal_two_arrays(t_mat[index], t_mat_model[index])

    
    


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_scoring_history_model_summary)
else:
    test_scoring_history_model_summary()
