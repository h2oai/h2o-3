import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# Given arrays of alpha and no lambda, the lambdas will be estimated as the max of gradient divided by
# the corresponding alpha value.  
#
# When an array of alpha and/or lambdas are given, a list of submodels are also built.  For each submodel built, only
# the coefficients, lambda/alpha/deviance values are returned.  The model metrics is calculated for the submodel
# with the best deviance.  
#
# In this test, in addition, we build separate models using just one lambda and one alpha values as when building one
# submodel.  In theory, the coefficients obtained from the separate models should equal to the submodels.  We check 
# and compare the followings:
# 1. coefficients from submodels and individual model should match when they are using the same alpha/lambda value;
# 2. validation metrics/training metrics from alpha array should equal to the individual model matching the alpha/lambda value;

def glm_alpha_array_lambda_null():
    # compare coefficients and deviance when only training dataset is available
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    for ind in range(10):
        train[ind] = train[ind].asfactor()
    train["C21"] = train["C21"].asfactor()
    frames = train.split_frame(ratios=[0.8],seed=12345)
    d = frames[0]
    d_test = frames[1]
    regKeys = ["alphas", "lambdas", "explained_deviance_valid", "explained_deviance_train"]
    
    # compare results when validation dataset is present
    mLVal = glm(family='binomial',alpha=[0.1,0.5,0.9],solver='COORDINATE_DESCENT') # train with validations set
    mLVal.train(training_frame=d,x=list(range(20)),y=20, validation_frame=d_test)
    rVal = glm.getGLMRegularizationPath(mLVal)
    best_submodel_indexVal = mLVal._model_json["output"]["best_submodel_index"]
    m2Val = glm.makeGLMModel(model=mLVal,coefs=rVal['coefficients'][best_submodel_indexVal])
    dev1Val = rVal['explained_deviance_valid'][best_submodel_indexVal]
    p2Val = m2Val.model_performance(d_test)
    dev2Val = 1-p2Val.residual_deviance()/p2Val.null_deviance()
    assert abs(dev1Val - dev2Val) < 1e-6
    
    for l in range(0,len(rVal['lambdas'])):       
        # test with validation dataset
        mVal = glm(family='binomial',alpha=[rVal['alphas'][l]],Lambda=[rVal['lambdas'][l]],solver='COORDINATE_DESCENT')
        mVal.train(training_frame=d,x=list(range(20)),y=20, validation_frame = d_test)
        mrVal= glm.getGLMRegularizationPath(mVal)
        csVal = rVal['coefficients'][l]
        cs_normVal = rVal['coefficients_std'][l]
        pyunit_utils.assertEqualCoeffDicts(csVal, mVal.coef(), tol=1e-2)
        pyunit_utils.assertEqualCoeffDicts(cs_normVal, mVal.coef_norm(), tol=1e-2)
        p = mVal.model_performance(d_test)
        devmVal = 1-p.residual_deviance()/p.null_deviance()
        devnVal = rVal['explained_deviance_valid'][l]
        assert abs(devmVal - devnVal) < 1e-4
        pyunit_utils.assertEqualRegPaths(regKeys, rVal, l, mrVal, tol=1e-4)
        if (l == best_submodel_indexVal): # check training metrics, should equal for best submodel index
            pyunit_utils.assertEqualModelMetrics(mVal._model_json["output"]["validation_metrics"],
                                                 mLVal._model_json["output"]["validation_metrics"], tol=1e-2)
        else: # for other submodel, should have worse residual_deviance() than best submodel
            assert p.residual_deviance() >= p2Val.residual_deviance(), "Best submodel does not have lowerest " \
                                                                    "residual_deviance()!"
            
if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_alpha_array_lambda_null)
else:
    glm_alpha_array_lambda_null()
