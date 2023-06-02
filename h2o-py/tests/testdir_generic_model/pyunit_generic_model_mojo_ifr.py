import tempfile
import os
import sys
sys.path.insert(1,"../../")

import h2o
from h2o.display import H2OTableDisplay, capture_output
from h2o.estimators import H2OIsolationForestEstimator, H2OGenericEstimator
from tests import pyunit_utils
from tests.testdir_generic_model import compare_output, compare_params


def mojo_model_ifr_test():

    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    ifr = H2OIsolationForestEstimator(ntrees=1)
    ifr.train(x = ["Origin", "Dest"], y = "Distance", training_frame=airlines)
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (original_output, _):
        ifr.show()
    print(original_output.getvalue())

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = ifr.download_mojo(original_model_filename)
      
    model = H2OGenericEstimator.from_file(original_model_filename)
    assert model is not None
    with H2OTableDisplay.pandas_rendering_enabled(False), capture_output() as (generic_output, _):
        model.show()
    print(generic_output.getvalue())
    compare_params(ifr, model)

    strip_part = "Model Summary: "
    algo_name = 'ModelMetricsAnomaly: isolationforest'
    generic_algo_name = 'ModelMetricsAnomaly: generic'

    compare_output(original_output.getvalue(), generic_output.getvalue(), strip_part, algo_name, generic_algo_name)
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
