import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
from tests import pyunit_utils
from tests.pyunit_utils import utils_for_glm_tests

# In this test, I set both the beta and linear constraints to be very loose and they are inactive when the correct GLM
#  coefficients are found.  The following tests are run:
# 1. without constraints, regularization, I want to find the optimal GLM coefficients;
# 2. with constraints, regularization and initializing the constraint GLM run with the optimal coefficients 
#    obtained in test 1 by setting the parameter init_optimal_glm=True, grab the model coefficients
# 3. with constraints, regularization and with default GLM coefficient initialization, grab model coefficients
#
# In addition, it is pointed out to me that when there are only beta constraints, the optimal solution is just let the
# GLM model building run and then at the end of each iteration, apply the beta constraints to the coefficients.  Now
# with linear constraints, I was wondering if I can still separately resolve the beta and linear constraints.  The 
# reason I am trying to figure this one out is because one beta constraint can have the potential of adding two linear
# constraints.  To decrease model building run time, I am trying to see if there are any performance difference if
# I ran linear and beta constraints separately and together.  Hence, I added the following test:
# 4. with constraints, regularization and initializing the constraint GLM run with the optimal coefficients 
#    obtained in test 1 by setting the parameter init_optimal_glm=True, and separate_linear_beta=True (to separate 
#    beta and linear constraints process), grab the model coefficients;
# 5. with constraints, regularization and with default GLM coefficient initialization, and separate_linear_beta=True, 
#    grab the model coefficients.
#
# The hope here is to find that all the coefficients from the models are the same or very close as the constraints
# are very loose and are inactive.
def test_constraints_binomial():
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
    types = "Equal"
    contraint_numbers = 0
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "C10.1"
    values = -0.3
    types = "Equal"
    contraint_numbers = 0
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -1.0044392439227687
    types = "Equal"
    contraint_numbers = 0
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "C18"
    values = 0.75
    types = "Equal"
    contraint_numbers = 1
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "C20"
    values = -0.13
    types = "Equal"
    contraint_numbers = 1
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -6.941462080282776
    types = "Equal"
    contraint_numbers = 1
    loose_init_const.append([name, values, types, contraint_numbers])

    # add loose constraints
    name = "C19"
    values = 0.5
    types = "LessThanEqual"
    contraint_numbers = 2
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "C20"
    values = -0.8
    types = "LessThanEqual"
    contraint_numbers = 2
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -10
    types = "LessThanEqual"
    contraint_numbers = 2
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "C12"
    values = 2
    types = "LessThanEqual"
    contraint_numbers = 3
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "C13"
    values = -3
    types = "LessThanEqual"
    contraint_numbers = 3
    loose_init_const.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -36
    types = "LessThanEqual"
    contraint_numbers = 3
    loose_init_const.append([name, values, types, contraint_numbers])    
    
    linear_constraints2 = h2o.H2OFrame(loose_init_const)
    linear_constraints2.set_names(["names", "values", "types", "constraint_numbers"])    
    # GLM model with with GLM coefficients set to GLM model coefficients built without constraints
    h2o_glm_optimal_init = glm(family="binomial", lambda_=0.0, seed=12345, remove_collinear_columns=True,solver="irlsm", 
                               linear_constraints=linear_constraints2, init_optimal_glm=True)
    h2o_glm_optimal_init.train(x=predictors, y=response, training_frame=train)
    init_logloss = h2o_glm_optimal_init.model_performance()._metric_json['logloss']
    print("logloss with constraints and coefficients initialized with glm model built without constraints: {0}".format(init_logloss))
    print(glm.getConstraintsInfo(h2o_glm_optimal_init))

    # GLM model with with GLM coefficients set to random GLM model coefficients
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
    obj_esp_list = [0.01, 0.05, 0.1]
    inner_loop_number = [1, 2, 3, 4, 5, 6, 8, 10, 15]
    h2o_glm_random_init = utils_for_glm_tests.constraint_glm_gridsearch(train, predictors, response, solver="IRLSM",
                                                                        family="binomial",
                                                                        linear_constraints=linear_constraints2,
                                                                        startval=random_coef)
    init_random_logloss = h2o_glm_random_init.model_performance()._metric_json['logloss']
    print("logloss with constraints and coefficients initialized random initial values: {0}, number of iterations"
          " taken to build the model: {1}".format(init_random_logloss, utils_for_glm_tests.find_glm_iterations(h2o_glm_random_init)))
    print(glm.getConstraintsInfo(h2o_glm_random_init))
    
    # GLM model with GLM coefficients with default initialization
    h2o_glm_default_init = utils_for_glm_tests.constraint_glm_gridsearch(train, predictors, response, solver="IRLSM",
                                                                         family="binomial",
                                                                         linear_constraints=linear_constraints2)
    default_init_logloss = h2o_glm_default_init.model_performance()._metric_json['logloss']
    print("logloss with constraints and default coefficients initialization: {0}, number of iterations"
          " taken to build the model: {1}".format(default_init_logloss, utils_for_glm_tests.find_glm_iterations(h2o_glm_default_init)))
    print(glm.getConstraintsInfo(h2o_glm_default_init))

    assert abs(logloss-init_logloss)<1e-6, "logloss from optimal GLM {0} and logloss from GLM with loose constraints " \
                                           "and initialized with optimal GLM {1} should equal but is not." \
                                           "".format(logloss, init_logloss)
    assert logloss<=init_random_logloss, "logloss from optimal GLM {0} should be lower than GLM with constraints " \
                                                   "and with random initial coefficients {1} but is" \
                                                   " not.".format(logloss, init_random_logloss)
    assert logloss<=default_init_logloss, "logloss from optimal GLM {0} should be less than GLM with constraints " \
                                            "and with default initial coefficients {1} but is" \
                                            " not.".format(logloss, default_init_logloss)

    assert pyunit_utils.equal_two_dicts(h2o_glm_optimal_init.coef(), h2o_glm.coef(), throwError=False), \
        "GLM coefficients are different!" # initializing GLM with optimal GLM coeff without constraints performs the best
    assert pyunit_utils.equal_two_dicts(h2o_glm_random_init.coef(), h2o_glm.coef(), tolerance=2.6e-2, throwError=False), \
        "GLM coefficients are different!" # initializing GLM with random coeff performs better than using default initialization
    assert pyunit_utils.equal_two_dicts(h2o_glm_default_init.coef(), h2o_glm.coef(), tolerance=6.6e-3, throwError=False), \
        "GLM coefficients are different!"


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_constraints_binomial)
else:
    test_constraints_binomial()
