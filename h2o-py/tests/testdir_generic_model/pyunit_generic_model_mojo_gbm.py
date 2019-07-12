import h2o
import tempfile
import os
from h2o.estimators import H2OGradientBoostingEstimator, H2OGenericEstimator
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
        
def compare_multinomial_output(original, generic):
    original = original[original.find("'Model Summary: '"):].replace('ModelMetricsMultinomial: gbm','').strip()
    generic = generic[generic.find("'Model Summary: '"):].replace('ModelMetricsMultinomialGeneric: generic', '').strip()
    assert generic == original

def mojo_model_test_multinomial():

    # GBM
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    gbm = H2OGradientBoostingEstimator(ntrees = 1)
    gbm.train(x = ["Origin", "Distance"], y = "Dest", training_frame=airlines)
    print(gbm)
    with Capturing() as original_output:
        gbm.show()

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = gbm.download_mojo(original_model_filename)
    
    key = h2o.lazy_import(original_model_filename)
    fr = h2o.get_frame(key[0])
    model = H2OGenericEstimator(model_key = fr)
    model.train()
    print(model)
    with Capturing() as generic_output:
        model.show()
    
    compare_multinomial_output(str(original_output), str(generic_output))
    
    predictions = model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert model._model_json["output"]["variable_importances"] is not None
    assert len(model._model_json["output"]["variable_importances"]._cell_values) > 0
    assert model._model_json["output"]["model_summary"] is not None
    assert len(model._model_json["output"]["model_summary"]._cell_values) > 0
    
    # Test constructor generating the model from existing MOJO file
    model = H2OGenericEstimator.from_file(original_model_filename)
    assert model is not None
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


def compare_binomial_output(original, generic):
    # The table from the MOJO uses 0/1 instead of YES/NO, which is the only difference. Values must be the same
    original = original[original.find("'Model Summary: '"):].replace('ModelMetricsBinomial: gbm','')\
        .replace("YES", '1  ')\ 
        .replace("NO", '0 ')\
        .strip()
    generic = generic[generic.find("'Model Summary: '"):].replace('ModelMetricsBinomialGeneric: generic', '').strip()
    assert generic == original
    
def mojo_model_test_binomial():

    # GBM
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    gbm = H2OGradientBoostingEstimator(ntrees = 1)
    gbm.train(x = ["Origin", "Distance"], y = "IsDepDelayed", training_frame=airlines)
    print(gbm)
    with Capturing() as original_output:
        gbm.show()

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = gbm.download_mojo(original_model_filename)

    key = h2o.lazy_import(original_model_filename)
    fr = h2o.get_frame(key[0])
    model = H2OGenericEstimator(model_key = fr)
    model.train()
    print(model)
    with Capturing() as generic_output:
        model.show()

    compare_binomial_output(str(original_output), str(generic_output))

    predictions = model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert model._model_json["output"]["variable_importances"] is not None
    assert len(model._model_json["output"]["variable_importances"]._cell_values) > 0
    assert model._model_json["output"]["model_summary"] is not None
    assert len(model._model_json["output"]["model_summary"]._cell_values) > 0

    # Test constructor generating the model from existing MOJO file
    model = H2OGenericEstimator.from_file(original_model_filename)
    assert model is not None
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
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_test_binomial)
else:
    mojo_model_test_multinomial()
