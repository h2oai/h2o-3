import sys
sys.path.insert(1,"../../")
import h2o
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator, H2OGenericEstimator
from tests import pyunit_utils

# Test of MOJO convenience methods
def generic_blank_constructor():
    
    # Train a model
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    model = H2OGradientBoostingEstimator(ntrees = 1)
    model.train(x = ["Origin", "Dest"], y = "IsDepDelayed", training_frame=airlines)
    
    #Save the previously created model into a temporary file
    original_model_filename = tempfile.mkdtemp()
    original_model_filename = model.download_mojo(original_model_filename)
    
    # Load the model from the temporary using an empty constructor
    mojo_model = H2OGenericEstimator()
    mojo_model.path = original_model_filename
    mojo_model.train()
    assert isinstance(mojo_model, H2OGenericEstimator)

    assert mojo_model._model_json["output"]["original_model_identifier"] == "gbm"
    assert mojo_model._model_json["output"]["original_model_full_name"] == "Gradient Boosting Machine"

    # Test scoring is available on the model
    predictions = mojo_model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421

if __name__ == "__main__":
    pyunit_utils.standalone_test(generic_blank_constructor)
else:
    generic_blank_constructor()
