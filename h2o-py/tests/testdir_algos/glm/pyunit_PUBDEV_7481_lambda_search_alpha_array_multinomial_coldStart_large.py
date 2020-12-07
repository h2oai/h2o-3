import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# Given alpha array and lambda_search=True, test with cold_start=True for multinomials
def glm_alpha_array_lambda_null():
    # first test: compare coefficients and deviance
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    mL = glm(family='multinomial',alpha=[0.1,0.5,0.9], lambda_search=True, solver='COORDINATE_DESCENT', cold_start=True,
             nlambdas = 5)
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
        print("compare models for index {0}, alpha {1}, lambda{2}".format(l, r['alphas'][l], r['lambdas'][l]))
        m = glm(family='multinomial',alpha=[r['alphas'][l]],Lambda=[r['lambdas'][l]], solver='COORDINATE_DESCENT')
        m.train(training_frame=d,x=list(range(0,54)),y=54)
        mr = glm.getGLMRegularizationPath(m)
        cs = r['coefficients'][l]
        cs_norm = r['coefficients_std'][l]
        pyunit_utils.assertCoefEqual(cs, m.coef(),coefClassSet)
        pyunit_utils.assertCoefEqual(cs_norm, m.coef_norm(), coefClassSetNorm)
        devm = 1-m.residual_deviance()/m.null_deviance()
        devn = r['explained_deviance_train'][l]
        assert abs(devm - devn) < 1e-6
        pyunit_utils.assertEqualRegPaths(regKeys, r, l, mr)
        if (l == best_submodel_index): # check training metrics, should equal for best submodel index
            pyunit_utils.assertEqualModelMetrics(m._model_json["output"]["training_metrics"],
                                                 mL._model_json["output"]["training_metrics"],
                                                 keySet=["MSE","null_deviance", "logloss", "RMSE", "r2"], tol=5e-1)
        else: # for other submodel, should have worse residual_deviance() than best submodel
            assert devm <= r['explained_deviance_train'][best_submodel_index], "Best submodel does not best " \
                                                                    "explained_deviance_train!"
        
if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_alpha_array_lambda_null)
else:
    glm_alpha_array_lambda_null()
