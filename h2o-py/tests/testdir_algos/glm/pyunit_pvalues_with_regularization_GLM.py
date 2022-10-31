from __future__ import print_function
from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm


def p_values_with_regularization_check():
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    nlambdas = 5
    model = glm(family="binomial", nlambdas=nlambdas, lambda_search=True, compute_p_values=True)
    model1 = glm(family="binomial", lambda_=[0.5, 0.1], alpha=[0.5, 0.5], compute_p_values=True)
    model.train(x=list(range(2, 9)), y=1, training_frame=prostate)
    model1.train(x=list(range(2, 9)), y=1, training_frame=prostate)

    # check model's p-values
    p_values_model = model.coef_with_p_values()['p_value']
    print(p_values_model)
    p_values_model1 = model1.coef_with_p_values()['p_value']
    print(p_values_model1)

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


if __name__ == "__main__":
    pyunit_utils.standalone_test(p_values_with_regularization_check)
else:
    p_values_with_regularization_check()
