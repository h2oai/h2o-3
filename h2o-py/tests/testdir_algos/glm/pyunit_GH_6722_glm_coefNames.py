import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# This test is used to obtain the coefficient names that can be used to specify constraints for constrained GLM.
def test_glm_coefNames():
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname].asfactor()
    myY = "C21"
    myX = h2o_data.names.remove(myY)
    model = glm(max_iterations=0)
    model.train(x=myX, y=myY, training_frame=h2o_data)
    original_coef_names = model.coef_names()
    
    model2 = glm()
    model2.train(x=myX, y=myY, training_frame=h2o_data)
    names_model = list(model2.coef().keys())
    names_model.remove('Intercept')
    
    # both coefficients should equal
    assert original_coef_names == names_model, "Expected coefficients: {0}, actual: {1}".format(names_model, 
                                                                                                original_coef_names)
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_coefNames)
else:
    test_glm_coefNames()
