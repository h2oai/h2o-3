from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check and make sure that we can get the various model coefficients and variable importance
def test_gam_coeffs_varimp():
    print("Checking coefficients and variable importance for binomial")
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    buildModelCoeffVarimpCheck(h2o_data, myY, ["C11", "C12", "C13"], 'binomial')

    print("Checking coefficients and variable importance for gaussian")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    buildModelCoeffVarimpCheck(h2o_data, myY, ["C11", "C12", "C13"], 'gaussian')
    print("gam coeff/varimp test completed successfully")    


def buildModelCoeffVarimpCheck(train_data, y, gamX, family):
    numKnots = [5, 6, 7]
    spline_orders = [10, -1, 10]
    x=["C1","C2"]
    numPCoeffs = len(train_data["C1"].categories())+len(train_data["C2"].categories())
    numPCoeffs += numKnots[0]+spline_orders[0]-2    # due to I-spline
    numPCoeffs += numKnots[1]-1                     # due to CS spline
    numPCoeffs += numKnots[2]+spline_orders[2]-2-1  # due to M-splines, minus 1 due to centering
    numPCoeffs += 1  # intercept term
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX,  scale = [1,1,1], bs=[2,0,3],
                                                spline_orders=[10,-1,10],num_knots=numKnots)
    h2o_model.train(x=x, y=y, training_frame=train_data)
    h2oCoeffs = h2o_model.coef()
        
    assert len(h2oCoeffs)==numPCoeffs, "expected number of coefficients: {0}, actual number of coefficients: " \
                                      "{1}".format(numPCoeffs, len(h2oCoeffs))
    h2oCoeffsStandardized = h2o_model.coef_norm()
    assert len(h2oCoeffsStandardized)==numPCoeffs, "expected number of coefficients: {0}, actual number of " \
                                                  "coefficients:{1}".format(numPCoeffs, len(h2oCoeffsStandardized))
    varimp = h2o_model.varimp()
    # exclude the intercept term here
    assert len(varimp)==(numPCoeffs-1), "expected number of coefficients: {0}, actual number of " \
                                   "coefficients:{1}".format(numPCoeffs-1, len(varimp))


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_coeffs_varimp)
else:
    test_gam_coeffs_varimp()
