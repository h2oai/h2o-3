from __future__ import division
from __future__ import print_function
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

    print("Checking coefficients and variable importance for multinomial")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C11"
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    buildModelCoeffVarimpCheck(h2o_data, myY, ["C6", "C7", "C8"], 'multinomial')
    
    print("gam coeff/varimp test completed successfully")    


def buildModelCoeffVarimpCheck(train_data, y, gamX, family):
    numKnots = [5,6,7]
    x=["C1","C2"]
    numPCoeffs = len(train_data["C1"].categories())+len(train_data["C2"].categories())+sum(numKnots)+1-len(numKnots)
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX,  scale = [1,1,1], num_knots=numKnots)
    h2o_model.train(x=x, y=y, training_frame=train_data)
    h2oCoeffs = h2o_model.coef()
    nclass = 1
    if family == 'multinomial':
        nclass = len(train_data[y].categories())  
        h2oCoeffs = h2oCoeffs['coefficients']
        
    assert len(h2oCoeffs)==numPCoeffs*nclass, "expected number of coefficients: {0}, actual number of coefficients: " \
                                      "{1}".format(numPCoeffs*nclass, len(h2oCoeffs))
    h2oCoeffsStandardized = h2o_model.coef_norm()
    if family == 'multinomial':
        h2oCoeffsStandardized = h2oCoeffsStandardized['standardized_coefficients']
    assert len(h2oCoeffsStandardized)==numPCoeffs*nclass, "expected number of coefficients: {0}, actual number of " \
                                                  "coefficients:{1}".format(numPCoeffs*nclass, len(h2oCoeffsStandardized))
    varimp = h2o_model.varimp()
    # exclude the intercept term here
    assert len(varimp)==(numPCoeffs-1), "expected number of coefficients: {0}, actual number of " \
                                   "coefficients:{1}".format(numPCoeffs-1, len(varimp))


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_coeffs_varimp)
else:
    test_gam_coeffs_varimp()
