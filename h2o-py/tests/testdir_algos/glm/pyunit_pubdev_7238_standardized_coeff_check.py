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
    for ind in range(enumCols): # first half of the columns are enums
        training_data[ind] = training_data[ind].asfactor()
    model1 = H2OGeneralizedLinearEstimator(family=family, standardize=True)
    model1.train(training_frame=training_data, x=x, y=Y)
    stdCoeff1 = model1.coef_norm()
    
    # standardize numerical columns here
    for ind in range(enumCols, Y): # change the numerical columns to have mean 0 and std 1
        aver = training_data[ind].mean()
        sigma = 1.0/math.sqrt(training_data[ind].var())
        training_data[ind] = (training_data[ind]-aver)*sigma

    model2 = H2OGeneralizedLinearEstimator(family=family, standardize=False)
    model2.train(training_frame=training_data, x=x, y=Y)
    #coeff2 = model2.coef_norm() # this will crash before Zuzana fix, please use this one after your fix.
    coeff2 = model2.coef()  # Zuzana: remove this one and use the above after you are done with your fix.

    if family == 'multinomial': # special treatment, it contains a dict of dict
        assert len(stdCoeff1) == len(coeff2), "Coefficient dictionary lengths are different.  One has length {0} while" \
                                              " the other one has length {1}.".format(len(stdCoeff1), len(coeff2))
        for name in stdCoeff1.keys():
            pyunit_utils.equal_two_dicts(stdCoeff1[name], coeff2[name[4:]])
    else:
        pyunit_utils.equal_two_dicts(stdCoeff1, stdCoeff1)

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_standardized_coeffs)
else:
    test_standardized_coeffs()
