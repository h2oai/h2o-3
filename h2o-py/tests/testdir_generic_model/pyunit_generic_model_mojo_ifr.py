import h2o
import tempfile
import os
from h2o.estimators import H2OIsolationForestEstimator, H2OGenericEstimator
from tests import pyunit_utils
from tests.testdir_generic_model import compare_output, Capturing

def mojo_model_ifr_test():

    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    ifr = H2OIsolationForestEstimator(ntrees=1)
    ifr.train(x = ["Origin", "Dest"], y = "Distance", training_frame=airlines)
    print(ifr)
    with Capturing() as original_output:
        ifr.show()

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = ifr.download_mojo(original_model_filename)
      
    model = H2OGenericEstimator.from_file(original_model_filename)
    assert model is not None
    print(model)
    with Capturing() as generic_output:
        model.show()

    original_output_as_str = str(original_output)
    generic_output_without_model_parameters_as_str = str(generic_output).split(', \'Model parameters', 1)[0]+']'

    strip_part = "'Model Summary: '"
    algo_name = 'ModelMetricsAnomaly: isolationforest'
    generic_algo_name = 'ModelMetricsAnomaly: generic'

    compare_output(original_output_as_str, generic_output_without_model_parameters_as_str, strip_part, algo_name, generic_algo_name)
    predictions = model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert model._model_json["output"]["variable_importances"] is None
    assert model._model_json["output"]["model_summary"] is not None
    assert len(model._model_json["output"]["model_summary"]._cell_values) > 0

    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo");
    generic_mojo_filename = model.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(original_model_filename)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_ifr_test)
else:
    mojo_model_ifr_test()
