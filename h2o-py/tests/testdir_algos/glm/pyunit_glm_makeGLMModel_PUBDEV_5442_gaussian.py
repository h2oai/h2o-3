import sys
sys.path.insert(1,"../../../")
import h2o
from builtins import range
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def test_makeGLMModel():
    # read in the dataset and construct training set (and validation set)
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    myY = "GLEASON"
    myX = ["ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    m = glm(family='gaussian',Lambda=[0.001], alpha=[0.5])
    m.train(training_frame=d,x=myX,y= myY)
    r = glm.getGLMRegularizationPath(m)
    m2 = glm.makeGLMModel(model=m,coefs=r['coefficients'][0])
    f1 = m.predict(d)   # predict with original model
    f2 = m2.predict(d)  # predict with model out of makeGLMModel
    pyunit_utils.compare_frames_local(f1, f2 ,prob=1)
    coefs = r['coefficients'][0]
    coefs['wendy_dreams']=8
    
    try:
        glm.makeGLMModel(model=m,coefs=coefs)
        assert False, "Should have throw exception of bad coefficient length"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("Server error java.lang.IllegalArgumentException:" in temp) and \
               ("model coefficient length 9 is different from coefficient provided by user ") in temp, \
            "Wrong exception was received."
        print("coefficient test passed!")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_makeGLMModel)
else:
    test_makeGLMModel()
