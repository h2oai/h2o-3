import h2o
import tempfile
from h2o.estimators import H2OIsolationForestEstimator, H2OGenericEstimator
from tests import pyunit_utils


def mojo_model_irf_test():

    # GLM
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    irf = H2OIsolationForestEstimator(ntrees=1)
    irf.train(x = ["Origin", "Dest"], y = "Distance", training_frame=airlines)

    filename = tempfile.mkdtemp()
    filename = irf.download_mojo(filename)
      
    model = H2OGenericEstimator.from_mojo_file(filename)
    assert model is not None
    predictions = model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert model._model_json["output"]["variable_importances"] is None
    assert model._model_json["output"]["model_summary"] is not None
    assert len(model._model_json["output"]["model_summary"]._cell_values) > 0
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_irf_test)
else:
    mojo_model_irf_test()
