from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm


def p_values_with_regularization_check():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate[1] = prostate[1].asfactor()
    nlambdas = 5
    model = glm(family="binomial", nlambdas=nlambdas, lambda_search=True, compute_p_values=True)
    model1 = glm(family="binomial", lambda_=[0.5, 0.1], alpha=[0.5, 0.5], compute_p_values=True)
    model.train(x=list(range(2, 9)), y=1, training_frame=prostate)
    model1.train(x=list(range(2, 9)), y=1, training_frame=prostate)

    # check model's p-values
    p_values_model = model.coef_with_p_values()['p_value']
    # print(p_values_model)
    p_values_model1 = model1.coef_with_p_values()['p_value']
    # print(p_values_model1)

    assert p_values_model is not None
    assert len(p_values_model) > 0

    assert p_values_model1 is not None
    assert len(p_values_model1) > 0

    #  check p-values for submodels generated with lambda search
    r = glm.getGLMRegularizationPath(model)
    z_values_for_submodels = r['z_values']
    p_values_for_submodels = r['p_values']
    std_errs_for_submodels = r['std_errs']

    # check that z-values, p-values and std-err are not missing
    assert z_values_for_submodels is not None
    assert p_values_for_submodels is not None
    assert std_errs_for_submodels is not None

    # check that z-values, p-values and std-err are computed for each lambda
    assert len(z_values_for_submodels) == nlambdas
    assert len(p_values_for_submodels) == nlambdas
    assert len(std_errs_for_submodels) == nlambdas

    # check that for each submodel z-values, p-values and std-err are not empty
    for i in range(nlambdas):
        assert len(z_values_for_submodels[i]) > 0
        assert len(p_values_for_submodels[i]) > 0
        assert len(std_errs_for_submodels[i]) > 0

    #  check p-values for submodels generated without lambda search
    r = glm.getGLMRegularizationPath(model1)
    z_values_for_submodels = r['z_values']
    p_values_for_submodels = r['p_values']
    std_errs_for_submodels = r['std_errs']

    # check that z-values, p-values and std-err are not missing
    assert z_values_for_submodels is not None
    assert p_values_for_submodels is not None
    assert std_errs_for_submodels is not None

    # check that z-values, p-values and std-err are computed for each lambda
    assert len(z_values_for_submodels) == len(r['lambdas'])
    assert len(p_values_for_submodels) == len(r['lambdas'])
    assert len(std_errs_for_submodels) == len(r['lambdas'])

    # check that for each submodel z-values, p-values and std-err are not empty
    for i in range(len(r['lambdas'])):
        assert len(z_values_for_submodels[i]) > 0
        assert len(p_values_for_submodels[i]) > 0
        assert len(std_errs_for_submodels[i]) > 0


def p_values_with_lambda_search_check():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate[1] = prostate[1].asfactor()
    nlambdas = 5
    model = glm(family="binomial", nlambdas=nlambdas, lambda_search=True, compute_p_values=True)
    model.train(x=list(range(2, 9)), y=1, training_frame=prostate)
    r = glm.getGLMRegularizationPath(model)
    for l in range(len(r['lambdas'])):
        coefficients_lambda_search = r['coefficients'][l]
        z_values_lambda_search = r['z_values'][l]
        p_values_lambda_search = r['p_values'][l]
        std_errs_lambda_search = r['std_errs'][l]

        m = glm(family='binomial', lambda_search=False, lambda_=r['lambdas'][l], compute_p_values=True)
        m.train(x=list(range(2, 9)), y=1, training_frame=prostate)

        coefs = m.coef_with_p_values()
        coefficients_model = dict(zip(coefs["names"], coefs["coefficients"]))
        # print("coefficients_lambda_search: ", coefficients_lambda_search)
        # print("coefficients_model: ", coefficients_model)
        z_values_model = dict(zip(coefs["names"], coefs['z_value']))
        p_values_model = dict(zip(coefs["names"], coefs['p_value']))
        std_errs_model = dict(zip(coefs["names"], coefs['std_error']))
        assert z_values_lambda_search is not None
        assert p_values_lambda_search is not None
        assert std_errs_lambda_search is not None

        assert z_values_model is not None
        assert p_values_model is not None
        assert std_errs_model is not None
        # print("z_values_lambda_search: ", p_values_lambda_search)
        # print("z_values_model: ", p_values_model)

        # see the coefficions
        assert len(z_values_lambda_search) == len(z_values_model)
        assert len(p_values_lambda_search) == len(p_values_model)
        assert len(std_errs_lambda_search) == len(std_errs_model)
        # print("1: ", z_values_lambda_search)
        # print("2: ", z_values_model)

        epsilon_beta = 1e-4
        epsilon_values = 1e-2
        for name in r["names"]:
            # if coefficients are different, do not test z/pValues and stdErrs
            if abs(coefficients_model[name] - coefficients_lambda_search[name]) < epsilon_beta:
                if name in z_values_lambda_search and name in z_values_model:
                    assert ((str(z_values_lambda_search[name]).lower() == 'nan'
                             and str(z_values_model[name]).lower() == 'nan')
                            or (abs(z_values_lambda_search[name] - z_values_model[name]) < epsilon_values))
                if name in p_values_lambda_search and name in p_values_model:
                    assert ((str(p_values_lambda_search[name]).lower() == 'nan'
                             and str(p_values_model[name]).lower() == 'nan')
                            or (abs(p_values_lambda_search[name] - p_values_model[name]) < epsilon_values))
                if name in std_errs_lambda_search and name in std_errs_model:
                    assert ((str(std_errs_lambda_search[name]).lower() == 'nan'
                             and str(std_errs_model[name]).lower() == 'nan')
                            or (abs(std_errs_lambda_search[name] - std_errs_model[name]) < epsilon_values))


# test p-value calculation with regularization for maximum likelihood
def test_p_value_for_maximum_likelihood():
    training_data = h2o.import_file(
        "http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    weight = pyunit_utils.random_dataset_real_only(training_data.nrow, 1, realR=2, misFrac=0, randSeed=12345)
    weight = weight.abs()
    training_data = training_data.cbind(weight)
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model_ml = glm(family='gamma', lambda_=0, compute_p_values=True, dispersion_parameter_method="ml")
    model_ml.train(training_frame=training_data, x=x, y=Y)

    try:
        model_ml_reg = glm(family='gamma', lambda_=0.0000000001, compute_p_values=True, dispersion_parameter_method="ml")
        model_ml_reg.train(training_frame=training_data, x=x, y=Y)
        assert False, "Regularization is not supported with dispersion_parameter_method=\"ML\""
    except (OSError, EnvironmentError):
        return

    dispersion_factor_ml_estimated = model_ml._model_json["output"]["dispersion"]
    dispersion_factor_ml_reg_estimated = model_ml_reg._model_json["output"]["dispersion"]
    print(abs(dispersion_factor_ml_estimated-dispersion_factor_ml_reg_estimated))
    assert abs(dispersion_factor_ml_estimated-dispersion_factor_ml_reg_estimated) < 1e-4, \
        "Expected: {0}, Actual: {1}".format(dispersion_factor_ml_estimated, dispersion_factor_ml_reg_estimated)


if __name__ == "__main__":
    pyunit_utils.standalone_test(p_values_with_regularization_check)
    pyunit_utils.standalone_test(p_values_with_lambda_search_check)
    pyunit_utils.standalone_test(test_p_value_for_maximum_likelihood)
else:
    p_values_with_regularization_check()
    p_values_with_lambda_search_check()
    test_p_value_for_maximum_likelihood()
