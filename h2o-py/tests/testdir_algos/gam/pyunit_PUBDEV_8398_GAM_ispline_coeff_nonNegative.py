from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check to make sure the coefficients for I-splines are positive
def test_gam_nonNeg_coeffs():
    print("Checking logloss for binomial with different scale parameters")
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    buildModelCheckCoeff(h2o_data, myY, ["C11", "C12", "C13"], 'binomial')

    print("Checking mse for gaussian with different scale parameters")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    buildModelCheckCoeff(h2o_data, myY, ["C11", "C12", "C13"], 'gaussian')  

def buildModelCheckCoeff(train_data, y, gamX, family):
    numKnots = [3,4,5]
    x=["C1","C2"]
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX,  scale = [0.001, 0.001, 0.001],
                                                bs=[2,2,2], spline_orders=[2,3,4],num_knots=numKnots)
    h2o_model.train(x=x, y=y, training_frame=train_data)   
    # check to make sure gam column coefficients are non-negative
    coef_dict = h2o_model.coef()
    coef_keys = coef_dict.keys()
    for key in coef_keys:
        if "center" in key:
            assert coef_dict[key] >= 0
                

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_nonNeg_coeffs)
else:
    test_gam_nonNeg_coeffs()
