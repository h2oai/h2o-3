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
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    mL = glm(family='multinomial',alpha=[0.1,0.5,0.9])
    d[54] = d[54].asfactor()
    mL.train(training_frame=d,x=list(range(0,54)),y=54)
    r = glm.getGLMRegularizationPath(mL)
    regKeys = ["alphas", "lambdas", "explained_deviance_valid", "explained_deviance_train"]
    best_submodel_index = mL._model_json["output"]["best_submodel_index"]
    coefClassSet = ['coefs_class_0', 'coefs_class_1', 'coefs_class_2', 'coefs_class_3', 'coefs_class_4','coefs_class_5', 
                    'coefs_class_6', 'coefs_class_7']
    coefClassSetNorm = ['std_coefs_class_0', 'std_coefs_class_1', 'std_coefs_class_2', 'std_coefs_class_3', 
                        'std_coefs_class_4', 'std_coefs_class_5', 'std_coefs_class_6', 'std_coefs_class_7']
    for l in range(0,len(r['lambdas'])):
        m = glm(family='multinomial',alpha=[r['alphas'][l]],Lambda=[r['lambdas'][l]])
        m.train(training_frame=d,x=list(range(0,54)),y=54)
        mr = glm.getGLMRegularizationPath(m)
        cs = r['coefficients'][l]
        cs_norm = r['coefficients_std'][l]
        pyunit_utils.assertCoefEqual(cs, m.coef(),coefClassSet, tol=1e-5)
        pyunit_utils.assertCoefEqual(cs_norm, m.coef_norm(), coefClassSetNorm, tol=1e-5)
        devm = 1-m.residual_deviance()/m.null_deviance()
        devn = r['explained_deviance_train'][l]
        assert abs(devm - devn) < 1e-4
        pyunit_utils.assertEqualRegPaths(regKeys, r, l, mr)
        if (l == best_submodel_index): # check training metrics, should equal for best submodel index
            pyunit_utils.assertEqualModelMetrics(m._model_json["output"]["training_metrics"],
                                                 mL._model_json["output"]["training_metrics"],tol=1e-2)
        else: # for other submodel, should have worse residual_deviance() than best submodel
            assert m.logloss() >= mL.logloss(), "Best submodel does not have lowerest " \
                                                                    "logloss()!"
        
if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_alpha_array_lambda_null)
else:
    glm_alpha_array_lambda_null()
