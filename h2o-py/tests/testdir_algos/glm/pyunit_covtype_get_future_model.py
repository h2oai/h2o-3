import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import random

def test_get_future_model():
    
    

    covtype=h2o.upload_file(pyunit_utils.locate("smalldata/covtype/covtype.altered.gz"))

    myY=54
    myX=list(set(range(54)) - set([20,28]))   # Cols 21 and 29 are constant, so must be explicitly ignored

    # Set response to be indicator of a particular class
    res_class=random.sample(range(1,5), 1)[0]
    covtype[myY] = covtype[myY] == res_class
    covtype[myY] = covtype[myY].asfactor()

    # L2: alpha=0, lambda=0
    covtype_h2o1 = h2o.start_glm_job(y=covtype[myY], x=covtype[myX], family="binomial", alpha=[0], Lambda=[0])

    # Elastic: alpha=0.5, lambda=1e-4
    covtype_h2o2 = h2o.start_glm_job(y=covtype[myY], x=covtype[myX], family="binomial", alpha=[0.5], Lambda=[1e-4])

    # L1: alpha=1, lambda=1e-4
    covtype_h2o3 = h2o.start_glm_job(y=covtype[myY], x=covtype[myX], family="binomial", alpha=[1], Lambda=[1e-4])

    covtype_h2o1 = h2o.get_future_model(covtype_h2o1)
    print(covtype_h2o1)
    covtype_h2o2 = h2o.get_future_model(covtype_h2o2)
    print(covtype_h2o2)
    covtype_h2o3 = h2o.get_future_model(covtype_h2o3)
    print(covtype_h2o3)



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_get_future_model)
else:
    test_get_future_model()
