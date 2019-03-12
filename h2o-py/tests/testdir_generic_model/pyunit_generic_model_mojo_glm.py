import h2o
import tempfile
import os
from h2o.estimators import H2OGeneralizedLinearEstimator, H2OGenericEstimator
from tests import pyunit_utils


def mojo_model_glm_test():

    # GLM
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    glm = H2OGeneralizedLinearEstimator()
    glm.train(x = ["Origin", "Dest"], y = "Distance", training_frame=airlines)

    original_model_filename = tempfile.mkdtemp()
    original_model_filename = glm.download_mojo(original_model_filename)
      
    model = H2OGenericEstimator.from_mojo_file(original_model_filename)
    assert model is not None
    predictions = model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    assert model._model_json["output"]["model_summary"] is not None
    assert len(model._model_json["output"]["model_summary"]._cell_values) > 0

    generic_mojo_filename = tempfile.mkdtemp("zip", "genericMojo");
    generic_mojo_filename = model.download_mojo(path=generic_mojo_filename)
    assert os.path.getsize(generic_mojo_filename) == os.path.getsize(original_model_filename)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_glm_test)
else:
    mojo_model_glm_test()
