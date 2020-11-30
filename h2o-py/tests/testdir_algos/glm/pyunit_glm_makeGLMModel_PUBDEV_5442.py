import sys
sys.path.insert(1,"../../../")
import h2o
from builtins import range
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def test_makeGLMModel():
    # read in the dataset and construct training set (and validation set)
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    m = glm(family='binomial',Lambda=[0.001], alpha=[0.5],solver='COORDINATE_DESCENT')
    m.train(training_frame=d,x=[2,3,4,5,6,7,8],y=1)
    r = glm.getGLMRegularizationPath(m)
    m2 = glm.makeGLMModel(model=m,coefs=r['coefficients'][0])
    f1 = m.predict(d)   # predict with original model
    f2 = m2.predict(d)  # predict with model out of makeGLMModel
    pyunit_utils.compare_frames_local(f1[1],f2[1],prob=1)
    coefs = r['coefficients'][0]
    coefs['wendy_dreams']=8
    
    try:
        glm.makeGLMModel(model=m,coefs=coefs)
        assert False, "Test failed: should have throw exception of bad coefficient length!"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("Server error java.lang.IllegalArgumentException:" in temp) and \
               ("model coefficient length 8 is different from coefficient provided by user ") in temp,\
            "Wrong exception was received."                                 
        print("makeGLMModel test passed!")
        
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_makeGLMModel)
else:
    test_makeGLMModel()
