import sys

from tests.testdir_generic_model.common import drop_model_parameters_from_printout

sys.path.insert(1,"../../")
import h2o
import tempfile
import os

from h2o.estimators import H2OXGBoostEstimator, H2OGenericEstimator
from tests import pyunit_utils
from tests.testdir_generic_model import Capturing, compare_output


def test(x, y, output_test, strip_part, algo_name, generic_algo_name):
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    xgb = H2OXGBoostEstimator(ntrees=1, nfolds=3)
    xgb.train(x=x, y=y, training_frame=airlines, validation_frame=airlines)
    print(xgb)
    with Capturing() as original_output:
        xgb.show()
    
    original_model_filename = tempfile.mkdtemp()
    original_model_filename = xgb.download_mojo(original_model_filename)
    
    key = h2o.lazy_import(original_model_filename)
    fr = h2o.get_frame(key[0])
    generic_model = H2OGenericEstimator(model_key=fr)
    generic_model.train()
    print(generic_model)
    with Capturing() as generic_output:
        generic_model.show()

    original_output_as_str = str(original_output)
    generic_output_without_model_parameters_as_str = drop_model_parameters_from_printout(str(generic_output))

    output_test(original_output_as_str, generic_output_without_model_parameters_as_str, strip_part, algo_name, generic_algo_name)
    
    predictions = generic_model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert generic_model._model_json["output"]["variable_importances"] is not None
    assert len(generic_model._model_json["output"]["variable_importances"]._cell_values) > 0
    assert generic_model._model_json["output"]["model_summary"] is not None
    assert len(generic_model._model_json["output"]["model_summary"]._cell_values) > 0
    
    # Test constructor generating the model from existing MOJO file
    generic_model = H2OGenericEstimator.from_file(original_model_filename)
    assert generic_model is not None
    predictions = generic_model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert generic_model._model_json["output"]["variable_importances"] is not None
    assert len(generic_model._model_json["output"]["variable_importances"]._cell_values) > 0
    assert generic_model._model_json["output"]["model_summary"] is not None
    assert len(generic_model._model_json["output"]["model_summary"]._cell_values) > 0
    assert generic_model._parms is not None
    assert len(generic_model._parms._cell_values) > 0
    
    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo")
    generic_mojo_filename = generic_model.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(original_model_filename)
    
def mojo_model_test_regression():
    test(["Origin", "Dest"], "Distance", compare_output, "'Model Summary: '", 'ModelMetricsRegression: xgboost',
         'ModelMetricsRegressionGeneric: generic')

def mojo_model_test_binomial():
    test(["Origin", "Dest"], "IsDepDelayed", compare_output, "'Model Summary: '", 'ModelMetricsBinomial: xgboost',
         'ModelMetricsBinomialGeneric: generic')
    
def mojo_model_test_multinomial():
    test(["Origin", "Distance"], "Dest", compare_output, "'Model Summary: '", 'ModelMetricsMultinomial: xgboost',
         'ModelMetricsMultinomialGeneric: generic')

if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_test_binomial)
    pyunit_utils.standalone_test(mojo_model_test_multinomial)
    pyunit_utils.standalone_test(mojo_model_test_regression)

else:
    mojo_model_test_multinomial()
    mojo_model_test_binomial()
    mojo_model_test_regression()
