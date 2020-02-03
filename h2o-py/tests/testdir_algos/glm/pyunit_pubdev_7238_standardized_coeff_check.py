from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import math

# check varimp for Binomial, Multinomial, Regression when standardize is set to False.
# I did the following:
# 1. train GLM with a dataset with standardize = True
# 2. train GLM with a dataset with standardized numerical columns and with standardize = False
#
# The standardized coefficients from model 1 and the coefficients from model 2 should be the same in this case.
def test_standardized_coeffs():
    print("Checking standardized coefficients for multinomials....")
    buildModelCheckStdCoeffs("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv", "multinomial")

    print("Checking standardized coefficients for binomials....")
    buildModelCheckStdCoeffs("smalldata/glm_test/binomial_20_cols_10KRows.csv", "binomial")

    print("Checking standardized coefficients for regression....")
    buildModelCheckStdCoeffs("smalldata/glm_test/gaussian_20cols_10000Rows.csv", "gaussian")
 
def buildModelCheckStdCoeffs(training_fileName, family):
    training_data = h2o.import_file(pyunit_utils.locate(training_fileName))
    ncols = training_data.ncols
    Y = ncols-1
    x = list(range(0,Y))
    enumCols = Y/2
    if family == 'binomial' or family == 'multinomial':
        training_data[Y] = training_data[Y].asfactor()  #
    for ind in range(int(enumCols)): # first half of the columns are enums
        training_data[ind] = training_data[ind].asfactor()
    model1 = H2OGeneralizedLinearEstimator(family=family, standardize=True)
    model1.train(training_frame=training_data, x=x, y=Y)
    stdCoeff1 = model1.coef_norm()
    modelNS = H2OGeneralizedLinearEstimator(family=family, standardize=False)
    modelNS.train(training_frame=training_data, x=x, y=Y)

    coeffNSStandardized = modelNS.coef_norm()
    coeffNS = modelNS.coef()
    if family=='multinomial':
        nclass = len(coeffNS)
        for cind in range(nclass):
            coeff1PerClass = coeffNSStandardized["std_coefs_class_"+str(cind)]
            coeff2PerClass = coeffNS["coefs_class_"+str(cind)]
            print("Comparing multinomial coefficients for class {0}".format(cind))
            assert_coeffs_equal(coeff1PerClass, coeff2PerClass, training_data)
    else: # for binomial and gaussian
        assert_coeffs_equal(coeffNSStandardized, coeffNS, training_data)
    
    # standardize numerical columns here
    for ind in range(int(enumCols), Y): # change the numerical columns to have mean 0 and std 1
        aver = training_data[ind].mean()
        sigma = 1.0/math.sqrt(training_data[ind].var())
        training_data[ind] = (training_data[ind]-aver)*sigma

    model2 = H2OGeneralizedLinearEstimator(family=family, standardize=False)
    model2.train(training_frame=training_data, x=x, y=Y)
    coeff2 = model2.coef_norm()
    compare_coeffs_2_model(family, stdCoeff1, coeff2)   # make sure standardized coefficients from model 1 and 2 are the same

    # this part of the test is to check and make sure the changes I made int coef() and coef_norm() accurately
    # capture the correct coefficients.
    coeff2Coef = model2.coef() # = coeff2 since training data are standardized already
    compare_coeffs_2_model(family, coeff2, coeff2Coef, sameModel=True) # make sure coefficients from coef_norm and coef are the same

def compare_coeffs_2_model(family, coeff1, coeff2, sameModel=False):
    if family == 'multinomial': # special treatment, it contains a dict of dict
        assert len(coeff1) == len(coeff2), "Coefficient dictionary lengths are different.  One has length {0} while" \
                                          " the other one has length {1}.".format(len(coeff1), len(coeff2))
        if sameModel:   # coef_norm return std_class_ and coef return class_.  Hence, need to change key here
            coeff2CoefKeyChanged = dict()
            for index in range(len(coeff2)):
                key = "coefs_class_"+str(index)
                coeff2CoefKeyChanged['std_'+key]= coeff2[key]
            coeff2 = coeff2CoefKeyChanged
        
        for name in coeff1.keys():
            pyunit_utils.equal_two_dicts(coeff1[name], coeff2[name])
    else:
        pyunit_utils.equal_two_dicts(coeff1, coeff2)
    
def assert_coeffs_equal(coeffStandard, coeff, training_data):
    interceptOffset = 0
    for key in coeffStandard.keys():
        temp1 = coeffStandard[key]
        temp2 = coeff[key]
        if abs(temp1-temp2) > 1e-6:
            if not(key=="Intercept"):
                colIndex = int(float(key.split("C")[1]))-1
                interceptOffset = interceptOffset + temp2 * training_data[colIndex].mean()[0,0]
                temp2 = temp2 * math.sqrt(training_data[colIndex].var())
                assert abs(temp1-temp2) < 1e-6, "Expected: {0}, Actual: {1} at col: {2}".format(temp2, temp1, key)
    temp1 = coeffStandard["Intercept"]
    temp2 = coeff["Intercept"]+interceptOffset
    assert abs(temp1-temp2) < 1e-6, "Expected: {0}, Actual: {1} at Intercept".format(temp2, temp1)    

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_standardized_coeffs)
else:
    test_standardized_coeffs()
