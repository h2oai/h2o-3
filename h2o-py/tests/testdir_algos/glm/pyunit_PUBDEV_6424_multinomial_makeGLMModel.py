import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def test_glm_multinomial_makeGLMModel():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/covtype/covtype.20k.data"))
    mL = glm(family='multinomial',alpha=[0.1], Lambda=[0.9])
    d[54] = d[54].asfactor()
    mL.train(training_frame=d,x=list(range(0,54)),y=54)
    r = glm.getGLMRegularizationPath(mL)
    m2 = glm.makeGLMModel(model=mL,coefs=r['coefficients'][0]) # model generated from setting coefficients to model
    f1 = mL.predict(d)
    f2 = m2.predict(d)
    pyunit_utils.compare_frames_local(f1, f2, prob=1)
    
    coefs = r['coefficients'][0]
    coefs["wendy_dreams"] = 0.123 # add extra coefficients to model coefficient
    
    try:
        glm.makeGLMModel(model=mL, coefs=coefs)
        assert False, "Should have thrown an exception!"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("Server error java.lang.IllegalArgumentException:" in temp) and \
           ("model coefficient length 371 is different from coefficient provided by user") in temp, \
            "Wrong exception was received."
        print("glm Multinomial makeGLMModel test completed!")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_multinomial_makeGLMModel)
else:
    test_glm_multinomial_makeGLMModel()
