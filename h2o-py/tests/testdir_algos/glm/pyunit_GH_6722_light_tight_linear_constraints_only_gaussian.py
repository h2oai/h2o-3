import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
from tests import pyunit_utils
from tests.pyunit_utils import utils_for_glm_tests

# In this test, I setup loosely tight linear constraints only.  Next, I compare the performance with GLM initialized with
# default coefficients, GLM initialized with optimal GLM built without constraints and GLM initialized with random
# coefficients.
#
# These are the constraints
# 0.5C1.1-0.25C2.1 (=-16.99) <= -17.5, 
# 1.5C4.1+3C17-2C15 (=-35.6089) <= -37
# -0.5C12-1.5C13+2C14 (=-159.99) <= -160.5
# 0.25*C11-0.5*C18+0.75*C19 (=-18.07829604901079) == -18.5
def test_light_tight_linear_constraints_only_gaussian():
    h2o_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname].asfactor()
    myY = "C21"
    myX = h2o_data.names.remove(myY)


    light_tight_constraints = [] # this constraint is satisfied by default coefficient initialization

    h2o_glm = glm(family="gaussian", lambda_=0.0, solver="irlsm", seed=12345, standardize=True)
    h2o_glm.train(x=myX, y=myY, training_frame=h2o_data)
    rmse = h2o_glm.model_performance()._metric_json['RMSE']
    print("RMSE without constraints: {0}".format(rmse))

    # add tight constraints
    name = "C1.1"
    values = 0.5
    types = "LessThanEqual"
    contraint_numbers = 0
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "C2.1"
    values = -0.25
    types = "LessThanEqual"
    contraint_numbers = 0
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = 17.5 
    types = "LessThanEqual"
    contraint_numbers = 0
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "C4.1"
    values = 1.5
    types = "LessThanEqual"
    contraint_numbers = 1
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "C17"
    values = 3
    types = "LessThanEqual"
    contraint_numbers = 1
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "C15"
    values = -2
    types = "LessThanEqual"
    contraint_numbers = 1
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = 37
    types = "LessThanEqual"
    contraint_numbers = 1
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "C12"
    values = -0.5
    types = "LessThanEqual"
    contraint_numbers = 2
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "C13"
    values = -1.5
    types = "LessThanEqual"
    contraint_numbers = 2
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "C14"
    values = 2
    types = "LessThanEqual"
    contraint_numbers = 2
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = 160.5
    types = "LessThanEqual"
    contraint_numbers = 2
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "C11"
    values = 0.25
    types = "Equal"
    contraint_numbers = 3
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "C18"
    values = -0.5
    types = "Equal"
    contraint_numbers = 3
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "C19"
    values = 0.75
    types = "Equal"
    contraint_numbers = 3
    light_tight_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = 18.5
    types = "Equal"
    contraint_numbers = 3
    light_tight_constraints.append([name, values, types, contraint_numbers])

    linear_constraints2 = h2o.H2OFrame(light_tight_constraints)
    linear_constraints2.set_names(["names", "values", "types", "constraint_numbers"])
    obj_esp_list = [0.01, 0.05, 0.1, 0.5, 1, 2]
    inner_loop_number = [1, 2, 3, 4, 5, 6, 8, 10, 15]
    # GLM model with with GLM coefficients set to GLM model coefficients built without constraints
    h2o_glm_optimal_init = utils_for_glm_tests.constraint_glm_gridsearch(h2o_data, myX, myY, solver="IRLSM",
                                                                         family="gaussian",
                                                                         linear_constraints=linear_constraints2,
                                                                         obj_eps_hyper=obj_esp_list,
                                                                         inner_loop_hyper=inner_loop_number,
                                                                         init_optimal_glm=True)
    optimal_init_MSE = h2o_glm_optimal_init.model_performance()._metric_json['RMSE']
    print("RMSE with optimal GLM coefficient initializaiton: {0}, number of iterations taken to build the model: "
          "{1}".format(optimal_init_MSE, utils_for_glm_tests.find_glm_iterations(h2o_glm_optimal_init)))
    print(glm.getConstraintsInfo(h2o_glm_optimal_init))
    # GLM model with GLM coefficients with default initialization
    h2o_glm_default_init = utils_for_glm_tests.constraint_glm_gridsearch(h2o_data, myX, myY, solver="IRLSM",
                                                                         family="gaussian",
                                                                         linear_constraints=linear_constraints2,
                                                                         obj_eps_hyper=obj_esp_list,
                                                                         inner_loop_hyper=inner_loop_number)
    default_init_MSE = h2o_glm_default_init.model_performance()._metric_json['RMSE']
    print("RMSE with default GLM coefficient initializaiton: {0}, number of iterations taken to build the model: "
          "{1}".format(default_init_MSE, utils_for_glm_tests.find_glm_iterations(h2o_glm_default_init)))
    print(glm.getConstraintsInfo(h2o_glm_default_init))
    constraint_eta0 = [0.1, 0.1258925, 0.2]
    constraint_tau = [2,5,10,15,20]
    constraint_alpha = [0.01, 0.1, 0.5]
    constraint_beta = [0.5, 0.9, 1.1]
    constraint_c0 = [2, 5, 10, 15, 20]    
    random_coef = [0.19109214,  0.00950836, -0.02040451, -0.10378078, -0.0124313 ,0.00304418,  0.00810864,  0.09461915,
                   -0.04671551, -0.0537097, -0.05873786,  0.12283965,  0.03239637,  0.05075875, -0.13135684, 0.02384002,  
                   0.14765001,  0.04634871,  0.06315379,  0.14218046, -0.04808092, -0.11047412, -0.09817918,  
                   0.21041207,  0.02009301, -0.06173337,  0.05589046, -0.09017402, -0.12810626,  0.05095339, -0.17032734, 
                   -0.09195688, -0.11409601,  0.01177211,  0.15978266, -0.17462904, -0.04463797]
    h2o_glm_random_init = utils_for_glm_tests.constraint_glm_gridsearch(h2o_data, myX, myY, solver="IRLSM",
                                                                         family="gaussian",
                                                                         linear_constraints=linear_constraints2,
                                                                         startval=random_coef,
                                                                        constraint_eta0=constraint_eta0,
                                                                        constraint_tau=constraint_tau,
                                                                        constraint_alpha=constraint_alpha,
                                                                        constraint_beta=constraint_beta,
                                                                        constraint_c0=constraint_c0)
    random_init_MSE = h2o_glm_random_init.model_performance()._metric_json['RMSE']
    print("RMSE with random GLM coefficient initializaiton: {0}, number of iterations taken to build the model: "
          "{1}".format(random_init_MSE, utils_for_glm_tests.find_glm_iterations(h2o_glm_random_init)))
    print(glm.getConstraintsInfo(h2o_glm_random_init))

    assert rmse <= optimal_init_MSE, "RMSE from optimal GLM {0} should be lower than RMSE from GLM with light tight" \
                                     " constraints and initialized with optimal GLM {1} but is not.".format(rmse, optimal_init_MSE)

    assert rmse <= default_init_MSE, "RMSE from optimal GLM {0} should be lower than RMSE from GLM with light tight" \
                                     " constraints and initialized with default coefficients GLM {1} but is " \
                                     "not.".format(rmse, optimal_init_MSE)

    assert rmse <= random_init_MSE, "RMSE from optimal GLM {0} should be lower than RMSE from GLM with light tight" \
                                     " constraints and initialized with random coefficients GLM {1} but is " \
                                     "not.".format(rmse, random_init_MSE)

    assert pyunit_utils.equal_two_dicts(h2o_glm_optimal_init.coef(), h2o_glm.coef(), throwError=False), \
        "GLM coefficients are different!"
    assert pyunit_utils.equal_two_dicts(h2o_glm_random_init.coef(), h2o_glm.coef(), tolerance=2e-4, throwError=False), \
        "GLM coefficients are different!"
    assert pyunit_utils.equal_two_dicts(h2o_glm_default_init.coef(), h2o_glm.coef(), tolerance=3e-4, throwError=False), \
        "GLM coefficients are different!" # again random coefficient intialization performs better than default initialization
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_light_tight_linear_constraints_only_gaussian)
else:
    test_light_tight_linear_constraints_only_gaussian()
