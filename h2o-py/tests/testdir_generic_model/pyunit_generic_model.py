import sys
sys.path.insert(1,"../../")
import h2o
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator, H2OGenericEstimator
from tests import pyunit_utils
from pandas.testing import assert_frame_equal


# Test of MOJO convenience methods
def generic_blank_constructor():

    # Train a model
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    airlines_test = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_test.csv"))
    model = H2OGradientBoostingEstimator(ntrees=10)
    model.train(x=["Origin", "Dest"], y="IsDepDelayed", training_frame=airlines)
    predictions = model.predict(airlines_test).as_data_frame(use_pandas=True)
    contributions = model.predict_contributions(airlines_test).as_data_frame(use_pandas=True)

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
    mojo_predictions = mojo_model.predict(airlines_test).as_data_frame(use_pandas=True)
    assert_frame_equal(predictions, mojo_predictions)

    # Test predict contributions is available on the model
    mojo_contributions = mojo_model.predict_contributions(airlines_test).as_data_frame(use_pandas=True)
    assert_frame_equal(contributions, mojo_contributions)


if __name__ == "__main__":
    pyunit_utils.standalone_test(generic_blank_constructor)
else:
    generic_blank_constructor()
