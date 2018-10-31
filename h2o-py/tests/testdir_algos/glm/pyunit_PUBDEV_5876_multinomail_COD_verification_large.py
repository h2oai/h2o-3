import sys
sys.path.insert(1,"../../../")
import h2o
from builtins import range
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def test_glm_multinomial():
    trainF = h2o.import_file(pyunit_utils.locate("bigdata/laptop/glm/multinomial20Class_10KRows.csv"))
    fixInt2Enum(trainF)
    y = trainF.ncol-1
    x = list(range(y))
    m_LS = glm(family='multinomial', seed=12345, solver="coordinate_descent", max_iterations=5)
    m_LS.train(training_frame=trainF, x=x, y=y)
    original_model = h2o.load_model(pyunit_utils.locate("bigdata/laptop/glm/GLM_model_python_1542751701212_3"))
    runtime_LS = m_LS._model_json["output"]["run_time"]/1000.0
    originalRT = original_model._model_json["output"]["run_time"]/1000.0
    originalCoeffs = original_model._model_json["output"]["coefficients_table"]
    m_LSCoeffs = m_LS._model_json["output"]["coefficients_table"]

    print("New Training time (s) with default multinomial settings and lambda search is {0}.".format(runtime_LS))
    print("Original Training time (s) with default multinomial settings and lambda search is {0}.".format(originalRT))
    pyunit_utils.assert_H2OTwoDimTable_equal_upto(originalCoeffs, m_LSCoeffs, m_LS._model_json["output"]["coefficients_table"]._col_header, tolerance=1e-10)
    print("MSE difference between the two models: {0}".format(abs(m_LS._model_json["output"]["training_metrics"]["MSE"]-original_model._model_json["output"]["training_metrics"]["MSE"])))

def fixInt2Enum(h2oframe):
    numCols = h2oframe.ncol

    for cind in range(numCols):
        ctype = h2oframe.type(cind)
        if ctype=='int':
            h2oframe[cind] = h2oframe[cind].asfactor()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_multinomial)
else:
    test_glm_multinomial()
