import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
from tests import pyunit_utils
from tests.pyunit_utils import utils_for_glm_tests

def test_loose_linear_constraints_binomial():
    '''
    check and make sure coefficients close to GLM built without constraints are generated with loose constraints
    that are satisfied with coefficients from GLM without constraints.  Only less than and equal to
     constraints are present.
    '''
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    for ind in range(10):
        train[ind] = train[ind].asfactor()
    train["C21"] = train["C21"].asfactor()
    response = "C21"
    predictors = list(range(0,20))

    loose_init_const = [] # this constraint is satisfied by default coefficient initialization

    h2o_glm = glm(family="binomial", remove_collinear_columns=True, lambda_=0.0, solver="irlsm", seed=12345,
                  standardize=True)
    h2o_glm.train(x=predictors, y=response, training_frame=train)
    logloss = h2o_glm.model_performance()._metric_json['logloss']
    print("logloss with no constraints: {0}".format(logloss))

    # add loose constraints
    name = "C19"
    values = 0.5
    types = "LessThanEqual"
    contraint_numbers = 0
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "C20"
    values = -0.8
    types = "LessThanEqual"
    contraint_numbers = 0
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -10
    types = "LessThanEqual"
    contraint_numbers = 0
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "C12"
    values = 2
    types = "LessThanEqual"
    contraint_numbers = 1
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "C13"
    values = -3
    types = "LessThanEqual"
    contraint_numbers = 1
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -36
    types = "LessThanEqual"
    contraint_numbers = 1
    loose_init_const.append([name, values, types, contraint_numbers])

    linear_constraints2 = h2o.H2OFrame(loose_init_const)
    linear_constraints2.set_names(["names", "values", "types", "constraint_numbers"])

    # GLM model with with GLM coefficients set to GLM model coefficients built without constraints
    h2o_glm_optimal_init = glm(family="binomial", remove_collinear_columns=True, lambda_=0.0, solver="irlsm", 
                               linear_constraints=linear_constraints2, seed=12345, init_optimal_glm=True)
    h2o_glm_optimal_init.train(x=predictors, y=response, training_frame=train)
    init_logloss = h2o_glm_optimal_init.model_performance()._metric_json['logloss']
    print("logloss with optimal GLM coefficient initializaiton: {0}".format(init_logloss))
    print(glm.getConstraintsInfo(h2o_glm_optimal_init))
    
    # GLM model with random initialization
    random_coef = [0.9740393731418461, 0.9021970400494406, 0.8337282995102272, 0.20588758679724872, 0.12522385214612453,
                   0.6390730524643073, 0.7055779213989253, 0.9004255614099713, 0.4075431157767999, 0.161093231584713,
                   0.15250197544465616, 0.7172682822215489, 0.60836236371404, 0.07086628306822396, 0.263719138602719,
                   0.16102036359390437, 0.0065987448849305075, 0.5881312311814277, 0.7836567678399617, 0.9104401158881326,
                   0.8432891635016235, 0.033440093086177236, 0.8514611306363931, 0.2855332934628241, 0.36525972112514427,
                   0.7526593301495519, 0.9963694184200753, 0.5614168317678196, 0.7950126291921057, 0.6212978800904426,
                   0.176936615687169, 0.8817788599562331, 0.13699370230879637, 0.5754950980437555, 0.1507294463182668,
                   0.23409699287029495, 0.6949148063429461, 0.47140569181488556, 0.1470896240551064, 0.8475557222612405,
                   0.05957485472498203, 0.07490903723892406, 0.8412381196460251, 0.26874846387453943, 0.13669341206289243,
                   0.8525684329438777, 0.46716360402752777, 0.8522055745422484, 0.3129394551398561, 0.908966336417204,
                   0.26259461196353984, 0.07245314277889847, 0.41429401839807156, 0.22772860293274222, 0.26662443208488784,
                   0.9875655504027848, 0.5832266083052889, 0.24205847206862052, 0.9843760682096272, 0.16269008279311103,
                   0.4941250734508458, 0.5446841276322587, 0.19222703209695946, 0.9232239752817498, 0.8824688635063289,
                   0.224690851359456, 0.5809304720756304, 0.36863807988348585]
    constraint_eta0 = [0.1]
    constraint_tau = [5]
    constraint_alpha = [0.01]
    constraint_beta = [0.5]
    constraint_c0 = [5, 10] # initial value  
    h2o_glm_random_init = utils_for_glm_tests.constraint_glm_gridsearch(train, predictors, response, solver="IRLSM",
                                                                        family="binomial",
                                                                        linear_constraints=linear_constraints2,
                                                                        startval=random_coef,
                                                                        constraint_eta0=constraint_eta0,
                                                                        constraint_tau=constraint_tau,
                                                                        constraint_alpha=constraint_alpha,
                                                                        constraint_beta=constraint_beta,
                                                                        constraint_c0=constraint_c0)
    random_init_logloss = h2o_glm_random_init.model_performance()._metric_json['logloss']
    print("logloss with random coefficient initializaiton: {0}, number of iterations taken to build the model: "
          "{1}".format(random_init_logloss, utils_for_glm_tests.find_glm_iterations(h2o_glm_random_init)))
    print(glm.getConstraintsInfo(h2o_glm_random_init))
    
    # GLM model with GLM coefficients with default initialization
    h2o_glm_default_init = utils_for_glm_tests.constraint_glm_gridsearch(train, predictors, response, solver="IRLSM",
                                                                         family="binomial",
                                                                         linear_constraints=linear_constraints2,
                                                                         constraint_eta0=constraint_eta0,
                                                                         constraint_tau=constraint_tau,
                                                                         constraint_alpha=constraint_alpha,
                                                                         constraint_beta=constraint_beta,
                                                                         constraint_c0=constraint_c0)
    default_init_logloss = h2o_glm_default_init.model_performance()._metric_json['logloss']
    print("logloss with default coefficient initializaiton: {0}, number of iterations taken to build the model: "
          "{1}".format(default_init_logloss, utils_for_glm_tests.find_glm_iterations(h2o_glm_default_init)))
    print(glm.getConstraintsInfo(h2o_glm_default_init))
    
    # since the constraints are loose, performance of GLM model without linear constraints and GLM model with linear
    # constraint and initialized with optimal GLM model coefficients should equal.  We will compare the logloss
    assert abs(logloss-init_logloss)<1e-6, "logloss from optimal GLM {0} and logloss from GLM with loose constraints " \
                                           "and initialized with optimal GLM {1} should equal but is not." \
                                           "".format(logloss, init_logloss)

    assert abs(logloss - random_init_logloss) < 1e-6, "logloss from optimal GLM {0} should equal to GLM with " \
                                                       "loose constraints and with random initial coefficients {1}" \
                                                       " but is not.".format(logloss, random_init_logloss)
    # both should get similar results because constraints is very loose
    assert abs(logloss - default_init_logloss) < 1e-6, "logloss from optimal GLM {0} should equal to GLM with " \
                                                       "loose constraints and with default initial coefficients {1}" \
                                                       " but is not.".format(logloss, default_init_logloss)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_loose_linear_constraints_binomial)
else:
    test_loose_linear_constraints_binomial()
