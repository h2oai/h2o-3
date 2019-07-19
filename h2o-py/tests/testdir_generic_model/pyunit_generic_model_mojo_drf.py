import h2o
import tempfile
import os
from h2o.estimators import H2ORandomForestEstimator, H2OGenericEstimator
from tests import pyunit_utils
from common import compare_multinomial_output, compare_binomial_output, compare_regression_output, Capturing

def test(x, y, output_test):

    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    drf = H2ORandomForestEstimator(ntrees=1, nfolds = 3)
    drf.train(x = x, y = y, training_frame=airlines, validation_frame=airlines)
    print(drf)

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = drf.download_mojo(original_model_filename)
      
    model = H2OGenericEstimator.from_file(original_model_filename)
    assert model is not None
    print(model)
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
    test(["Origin", "Dest"], "Distance", compare_regression_output)

def mojo_model_test_binomial():
    test(["Origin", "Dest"], "IsDepDelayed", compare_binomial_output)

def mojo_model_test_multinomial():
    test(["Origin", "Distance"], "Dest", compare_multinomial_output)
if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_test_binomial)
    pyunit_utils.standalone_test(mojo_model_test_multinomial)
    pyunit_utils.standalone_test(mojo_model_test_regression)
else:
    mojo_model_test_multinomial()
    mojo_model_test_binomial()
    mojo_model_test_regression()
