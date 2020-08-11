from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check and make sure that we can do scoring
def test_gam_model_predict():

    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    numKnots = [8,8,8]
    x = list(set(h2o_data.names) - {"response", "C11", "C12", "C13"})

    h2o_model_lambdaMinRatio10 = H2OGeneralizedAdditiveEstimator(family="gaussian", gam_columns=["C11", "C12", "C13"],  
                                                scale = [0.01, 0.01, 0.01], num_knots=numKnots, standardize=True, 
                                                lambda_search=True, lambda_min_ratio = 10, solver="irlsm")
    h2o_model_lambdaMinRatio10.train(x=x, y=myY, training_frame=h2o_data)

    h2o_model_lambdaMinRatio01 = H2OGeneralizedAdditiveEstimator(family="gaussian", gam_columns=["C11", "C12", "C13"],
                                                           scale = [0.01, 0.01, 0.01], num_knots=numKnots, 
                                                           standardize=True, lambda_search=True, 
                                                           lambda_min_ratio = 0.1, solver="irlsm")
    h2o_model_lambdaMinRatio01.train(x=x, y=myY, training_frame=h2o_data)
    
    assert h2o_model_lambdaMinRatio10.mse() >= h2o_model_lambdaMinRatio01.mse(), "Gam model with lambda_min_ratio=0.1 performs " \
                                                                   "better than lambda_min_ratio=10.  Shame!"
    
    

    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
