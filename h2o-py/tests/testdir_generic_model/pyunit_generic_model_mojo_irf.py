import h2o
import tempfile
import os
from h2o.estimators import H2OIsolationForestEstimator, H2OGenericEstimator
from tests import pyunit_utils
try:
    from StringIO import StringIO
except ImportError:
    from io import StringIO
import sys

# Required to work both in Py 2 & 3
class Capturing(list):
    def __enter__(self):
        self._stdout = sys.stdout
        sys.stdout = self._stringio = StringIO()
        return self
    def __exit__(self, *args):
        self.extend(self._stringio.getvalue().splitlines())
        del self._stringio    # free up some memory
        sys.stdout = self._stdout


def compare_output(original, generic):
    original = original[original.find("'Model Summary: '"):].replace('ModelMetricsMultinomial: gbm','').strip()
    generic = generic[generic.find("'Model Summary: '"):].replace('ModelMetricsMultinomialGeneric: generic', '').strip()
    assert generic == original

def mojo_model_irf_test():

    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    irf = H2OIsolationForestEstimator(ntrees=1)
    irf.train(x = ["Origin", "Dest"], y = "Distance", training_frame=airlines)
    print(irf)
    with Capturing() as original_output:
        irf.show()

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = irf.download_mojo(original_model_filename)
      
    model = H2OGenericEstimator.from_file(original_model_filename)
    assert model is not None
    print(model)
    with Capturing() as mojo_output:
        irf.show()
        
    compare_output(str(original_output), str(mojo_output))
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
    pyunit_utils.standalone_test(mojo_model_irf_test)
else:
    mojo_model_irf_test()
