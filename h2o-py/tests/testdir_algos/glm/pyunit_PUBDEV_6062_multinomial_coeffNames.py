import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def test_glm_multinomial_coeffs():
    trainF = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    y = "species"
    x = [0,1,2,3]
    bin_LS = glm(family='multinomial', seed=12345)
    bin_LS.train(x=x, y=y, training_frame=trainF)
    print(bin_LS.summary())
    coefficient_table_original = bin_LS._model_json["output"]["coefficients_table"]
    coefficient_table = bin_LS._model_json["output"]["coefficients_table_multinomials_with_class_names"]

    coeffNamesOld = coefficient_table_original.col_header
    coeffNames = coefficient_table.col_header
    validCoefficientNames = [u"names", u"coefs_class_Iris-setosa", u"coefs_class_Iris-versicolor",
                             u"coefs_class_Iris-virginica", u"std_coefs_class_Iris-setosa",
                             u"std_coefs_class_Iris-versicolor", u"std_coefs_class_Iris-virginica"]
    oldCoefficientNames = [u"names", u"coefs_class_0", u"coefs_class_1",
                             u"coefs_class_2", u"std_coefs_class_0",
                             u"std_coefs_class_1", u"std_coefs_class_2"]
    print(coefficient_table)
    print(coefficient_table_original)

    # compare coefficient names
    assert len(set(coeffNames).intersection(validCoefficientNames))==len(coeffNames),\
        "Expected coefficient names: {0}.  Actual coefficient names: {1}".format(validCoefficientNames, coeffNames)
    assert len(set(coeffNamesOld).intersection(oldCoefficientNames))==len(coeffNames), \
        "Expected original coefficient names: {0}.  Actual original coefficient names: " \
        "{1}".format(oldCoefficientNames, coeffNamesOld)

    # compare table contents to make sure they contain the same values
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(coefficient_table_original, coefficient_table, [u'coefs_class_0'],
                                                  tolerance=1e-10)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_multinomial_coeffs)
else:
    test_glm_multinomial_coeffs()
