import tempfile
import os
import sys
sys.path.insert(1,"../../")

import h2o
from h2o.display import H2OTableDisplay, capture_output
from h2o.estimators import H2ORandomForestEstimator, H2OGenericEstimator
from tests import pyunit_utils
from tests.testdir_generic_model import compare_output, compare_params


def test(x, y, output_test, strip_part, algo_name, generic_algo_name):

    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    drf = H2ORandomForestEstimator(ntrees=1, nfolds=3)
    drf.train(x=x, y=y, training_frame=airlines, validation_frame=airlines)
    
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (original_output, _):  # comparison fails when using pandas due to spaces formatting
        drf.show()
    print(original_output.getvalue())

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = drf.download_mojo(original_model_filename)
      
    model = H2OGenericEstimator.from_file(original_model_filename)
    assert model is not None
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (generic_output, _):
        model.show()
    print(generic_output.getvalue())
    compare_params(drf, model)

    output_test(original_output.getvalue(), generic_output.getvalue(), strip_part, algo_name, generic_algo_name)
    predictions = model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert model._model_json["output"]["variable_importances"] is not None
    assert len(model._model_json["output"]["variable_importances"]._cell_values) > 0
    assert model._model_json["output"]["model_summary"] is not None
    assert len(model._model_json["output"]["model_summary"]._cell_values) > 0

    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo");
    generic_mojo_filename = model.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(original_model_filename)


def mojo_model_test_regression():
    test(["Origin", "Dest"], "Distance", compare_output, "Model Summary: ", 'ModelMetricsRegression: drf',
         'ModelMetricsRegressionGeneric: generic')


def mojo_model_test_binomial():
    test(["Origin", "Dest"], "IsDepDelayed", compare_output, "Model Summary: ", 'ModelMetricsBinomial: drf',
         'ModelMetricsBinomialGeneric: generic')


def mojo_model_test_multinomial():
    test(["Origin", "Distance"], "Dest", compare_output, "Model Summary: ", 'ModelMetricsMultinomial: drf',
         'ModelMetricsMultinomialGeneric: generic')
    
    
pyunit_utils.run_tests([
    mojo_model_test_binomial,
    mojo_model_test_multinomial,
    mojo_model_test_regression
])
