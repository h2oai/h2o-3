import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# Given arrays of alpha and no lambda, the lambdas will be estimated as the max of gradient divided by
# the corresponding alpha value.  
#
# When an array of alpha and/or lambdas are given, a list of submodels are also built.  For each submodel built, only
# the coefficients, lambda/alpha/deviance values are returned.  The model metrics is calculated from the submodel
# with the best deviance.  
#
# In this test, in addition, we build separate models using just one lambda and one alpha values as when building one
# submodel.  In theory, the coefficients obtained from the separate models should equal to the submodels.  We check 
# and compare the followings:
# 1. coefficients from submodels and individual model should match when they are using the same alpha/lambda value;
# 2. training metrics from alpha array should equal to the individual model matching the alpha/lambda value;
def glm_alpha_array_lambda_null():
    # first test: compare coefficients and deviance
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    mL = glm(family='binomial',alpha=[0.1,0.5,0.9],solver='COORDINATE_DESCENT')
    mL.train(training_frame=d,x=[2,3,4,5,6,7,8],y=1)
    r = glm.getGLMRegularizationPath(mL)
    regKeys = ["alphas", "lambdas", "explained_deviance_valid", "explained_deviance_train"]
    best_submodel_index = mL._model_json["output"]["best_submodel_index"]
    m2 = glm.makeGLMModel(model=mL,coefs=r['coefficients'][best_submodel_index])
    dev1 = r['explained_deviance_train'][best_submodel_index]
    p2 = m2.model_performance(d)
    dev2 = 1-p2.residual_deviance()/p2.null_deviance()
    print(dev1," =?= ",dev2)
    assert abs(dev1 - dev2) < 1e-6
    for l in range(0,len(r['lambdas'])):
        m = glm(family='binomial',alpha=[r['alphas'][l]],Lambda=[r['lambdas'][l]],solver='COORDINATE_DESCENT')
        m.train(training_frame=d,x=[2,3,4,5,6,7,8],y=1)
        mr = glm.getGLMRegularizationPath(m)
        cs = r['coefficients'][l]
        cs_norm = r['coefficients_std'][l]
        pyunit_utils.assertEqualCoeffDicts(cs, m.coef())
        pyunit_utils.assertEqualCoeffDicts(cs_norm, m.coef_norm())
        p = m.model_performance(d)
        devm = 1-p.residual_deviance()/p.null_deviance()
        devn = r['explained_deviance_train'][l]
        assert abs(devm - devn) < 1e-4
        pyunit_utils.assertEqualRegPaths(regKeys, r, l, mr)
        if (l == best_submodel_index): # check training metrics, should equal for best submodel index
            pyunit_utils.assertEqualModelMetrics(m._model_json["output"]["training_metrics"],
                                                 mL._model_json["output"]["training_metrics"])
        else: # for other submodel, should have worse residual_deviance() than best submodel
            assert p.residual_deviance() >= p2.residual_deviance(), "Best submodel does not have lowerest " \
                                                                    "residual_deviance()!"
            
if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_alpha_array_lambda_null)
else:
    glm_alpha_array_lambda_null()
