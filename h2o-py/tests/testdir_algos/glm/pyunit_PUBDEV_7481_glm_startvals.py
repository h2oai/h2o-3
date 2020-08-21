import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
import math

# test startval to set GLM coefficients
def set_glm_startvals():
    # read in the dataset and construct training set (and validation set)
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    mL = glm(family='binomial')
    mL.train(training_frame=d,x=[2,3,4,5,6,7,8],y=1)
    mLcoeff = mL.coef()
    r = glm.getGLMRegularizationPath(mL)
    rcoeff = r["coefficients"][0]
    responseMean = d[1].mean()
    initIntercept = math.log(responseMean/(1.0-responseMean))
    startval1 = [0,0,0,0,0,0,0,initIntercept]
    startval2 = [rcoeff["AGE"], rcoeff["RACE"], rcoeff["DPROS"], rcoeff["DCAPS"], rcoeff["PSA"], rcoeff["VOL"], 
                rcoeff["GLEASON"], rcoeff["Intercept"]]
    startvalBad = [0,0]
    
    ml1 = glm(family="binomial", startval = startval1) # same starting condition as GLM
    ml1.train(training_frame=d,x=[2,3,4,5,6,7,8],y=1)
    ml1Coeff = ml1.coef()
    pyunit_utils.assertEqualCoeffDicts(mLcoeff, ml1Coeff , tol = 1e-6) # coeffs should be the same

    ml2 = glm(family="binomial", startval = startval2) # different starting condition from GLM
    ml2.train(training_frame=d,x=[2,3,4,5,6,7,8],y=1)
    ml2Coeff = ml2.coef()   
    
    try:
        pyunit_utils.assertEqualCoeffDicts(mLcoeff, ml2Coeff , tol = 1e-6)
        assert False, "Should have thrown an error as coefficients are different!"        
    except Exception as ex:
        print(ex)
    
    try:
        mlbad =  glm(family="binomial", startval = startvalBad)
        mlbad.train(training_frame=d,x=[2,3,4,5,6,7,8],y=1)
        assert False, "Should have thrown an error with bad GLM initial values!"
    except Exception as ex:
        print(ex)
        print("Test completed!  Success!")
            

if __name__ == "__main__":
    pyunit_utils.standalone_test(set_glm_startvals)
else:
    set_glm_startvals()
