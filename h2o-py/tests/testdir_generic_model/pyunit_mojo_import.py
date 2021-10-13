import h2o
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator, H2OGenericEstimator
from tests import pyunit_utils
import os
from pandas.testing import assert_frame_equal

# Test of MOJO convenience methods
def mojo_conveniece():    
    # Train a model
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    model = H2OGradientBoostingEstimator(ntrees = 1)
    model.train(x = ["Origin", "Dest"], y = "IsDepDelayed", training_frame=airlines)
    
    #Save the previously created model into a temporary file
    original_model_filename = tempfile.mkdtemp()
    original_model_filename = model.save_mojo(original_model_filename)
    
    # Load the model from the temporary file
    mojo_model = h2o.import_mojo(original_model_filename)
    assert isinstance(mojo_model, H2OGenericEstimator)
    
    # Test scoring is available on the model
    predictions = mojo_model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421

    #####
    # MOJO UPLOAD TEST
    #####

    # Download the MOJO
    original_model_filename = model.download_mojo(original_model_filename)
    # Load the model from the temporary file
    mojo_model = h2o.upload_mojo(original_model_filename)
    assert isinstance(mojo_model, H2OGenericEstimator)

    # Test scoring is available on the model
    predictions = mojo_model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421

    #####
    # MOJO to POJO Conversion test with POJO re-import
    #####

    pojo_directory = os.path.join(pyunit_utils.locate("results"), model.model_id + ".java")
    pojo_path = model.download_pojo(path = pojo_directory)
    mojo2_model = h2o.import_mojo(pojo_path)

    predictions2 = mojo2_model.predict(airlines)
    assert predictions2 is not None
    assert predictions2.nrows == 24421
    assert_frame_equal(predictions.as_data_frame(), predictions2.as_data_frame())


if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_conveniece)
else:
    mojo_conveniece()
