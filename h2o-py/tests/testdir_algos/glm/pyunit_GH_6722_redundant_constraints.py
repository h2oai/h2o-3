import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def test_redundant_constraints():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))  
    linear_constraints = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/linearConstraint3.csv"))
    enum_cols = [0,1,2,3,4,5,6,7,8,9]
    for colInd in enum_cols:
        d[colInd] = d[colInd].asfactor()
    try:
        m = glm(family='gaussian', max_iterations=1, linear_constraints=linear_constraints, solver="irlsm", 
                lambda_=0.0)
        m.train(training_frame=d,y= "C21")
        assert False, "Should have thrown exception of redundant constraints"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("redundant and possibly conflicting linear constraints" in temp), "Wrong exception was received."
        print("redundant constraint test passed!")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_redundant_constraints)
else:
    test_redundant_constraints()
