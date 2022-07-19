import tempfile
import os
import sys
sys.path.insert(1,"../../")

import h2o
from h2o.display import H2OTableDisplay, capture_output
from h2o.estimators import H2OGeneralizedLinearEstimator, H2OGenericEstimator
from tests import pyunit_utils
from tests.testdir_generic_model import compare_output, compare_params


def test(x, y, output_test, strip_part, algo_name, generic_algo_name, family):

    # GLM
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    glm = H2OGeneralizedLinearEstimator(nfolds = 2, family = family, max_iterations=2) # alpha = 1, lambda_ = 1, bad values, use default
    glm.train(x = x, y = y, training_frame=airlines, validation_frame=airlines, )
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (original_output, _):
        glm.show()
    print(original_output.getvalue())
    original_model_filename = tempfile.mkdtemp()
    original_model_filename = glm.download_mojo(original_model_filename)

    generic_mojo_model_from_file = H2OGenericEstimator.from_file(original_model_filename)
    assert generic_mojo_model_from_file is not None
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (generic_output, _):
        generic_mojo_model_from_file.show()
    print(generic_output.getvalue())
    compare_params(glm, generic_mojo_model_from_file)

    output_test(original_output.getvalue(), generic_output.getvalue(), strip_part, algo_name, generic_algo_name)
    predictions = generic_mojo_model_from_file.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert generic_mojo_model_from_file._model_json["output"]["model_summary"] is not None
    assert len(generic_mojo_model_from_file._model_json["output"]["model_summary"]._cell_values) > 0
    assert generic_mojo_model_from_file._model_json["output"]["variable_importances"] is not None
    assert len(generic_mojo_model_from_file._model_json["output"]["variable_importances"]._cell_values) > 0

    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo");
    generic_mojo_filename = generic_mojo_model_from_file.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(original_model_filename)


def mojo_model_test_binomial():
    test(["Origin", "Dest"], "IsDepDelayed", compare_output, 'GLM Model: summary', 'ModelMetricsBinomialGLM: glm',
         'ModelMetricsBinomialGLMGeneric: generic', 'binomial')


def mojo_model_test_regression():
    test(["Origin", "Dest"], "Distance", compare_output, 'GLM Model: summary', 'ModelMetricsRegressionGLM: glm',
         'ModelMetricsRegressionGLMGeneric: generic', 'gaussian')


def mojo_model_test_multinomial():
    test(["Origin", "Distance"], "Dest", compare_output, 'GLM Model: summary', 'ModelMetricsMultinomialGLM: glm',
         'ModelMetricsMultinomialGLMGeneric: generic', 'multinomial')


def mojo_model_test_ordinal():
    test(["Origin", "Distance", "IsDepDelayed"], "fDayOfWeek", compare_output, 'GLM Model: summary',
         'ModelMetricsOrdinalGLM: glm',
         'ModelMetricsOrdinalGLMGeneric: generic', 'ordinal')
    

pyunit_utils.run_tests([
    mojo_model_test_binomial,
    mojo_model_test_multinomial,
    mojo_model_test_regression,
    mojo_model_test_ordinal
])
