import sys
sys.path.insert(1,"../../../")
import h2o
from builtins import range
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

'''
PUBDEV-5876:  Simplify and speed up COD operations.
I train a binomial model with dataset and compare the coefficients at the end of the training to make sure they
agree to the ones before my changes are made.
'''
def test_glm_binomial():
    # check out binomial COD
    trainF = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm/binomial_binomial_training_set_enum_trueOneHot.csv.zip"))
    fixInt2Enum(trainF)
    y = trainF.ncol-1
    x = list(range(y))
    bin_LS = glm(family='binomial', seed=12345, solver="coordinate_descent")
    bin_LS.train(x=x, y=y, training_frame=trainF)
    originalModel = h2o.load_model(pyunit_utils.locate("bigdata/laptop/glm/GLM_model_python_1542751701212_1"))
    model_coefficients = pyunit_utils.extract_col_value_H2OTwoDimTable(bin_LS._model_json["output"]["coefficients_table"], "coefficients")
    runtime_LS = bin_LS._model_json["output"]["run_time"]/1000.0
    old_coefficients = pyunit_utils.extract_col_value_H2OTwoDimTable(originalModel._model_json["output"]["coefficients_table"], "coefficients")
    runtime_old = originalModel._model_json["output"]["run_time"]/1000.0
    print("New Training time (s) with default binomial settings and no lambda search is {0}".format(runtime_LS))
    print("Training time (s) with default binomial settings and no lambda search is {0}".format(runtime_old))

    pyunit_utils.equal_two_arrays(old_coefficients, model_coefficients, 1e-24, 1e-10)

def fixInt2Enum(h2oframe):
    numCols = h2oframe.ncol

    for cind in range(numCols):
        ctype = h2oframe.type(cind)
        if ctype=='int':
            h2oframe[cind] = h2oframe[cind].asfactor()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_binomial)
else:
    test_glm_binomial()
