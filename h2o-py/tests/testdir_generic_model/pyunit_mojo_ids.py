import h2o
import tempfile
from h2o.estimators import H2OGradientBoostingEstimator, H2OGenericEstimator
from tests import pyunit_utils, assert_equals, assert_not_equal


# Test of MOJO convenience methods
def test_mojo_ids():
    
    # Train a model
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    model = H2OGradientBoostingEstimator(ntrees=1)
    model.train(x=["Origin", "Dest"], y="IsDepDelayed", training_frame=airlines, verbose=False)
    
    # Save the previously created model into a temporary file
    original_model_filename = tempfile.mkdtemp()
    original_model_filename = model.save_mojo(original_model_filename)
    
    original_model_id = model.model_id
    print(original_model_id)
    
    # Import MOJO from the temporary file
    mojo_model = h2o.import_mojo(original_model_filename, model_id=original_model_id)
    print(mojo_model.model_id)
    assert_equals(mojo_model.model_id, original_model_id, "Ids should be the same.")
    
    # Download the MOJO
    original_model_filename = model.download_mojo(original_model_filename)
    
    # Upload MOJO from the temporary file
    mojo_model_up = h2o.upload_mojo(original_model_filename, model_id=original_model_id)
    print(mojo_model_up.model_id)
    assert_equals(mojo_model_up.model_id, original_model_id, "Ids should be the same.")
    
    # Load MOJO model from file
    mojo_model_from_file = H2OGenericEstimator.from_file(original_model_filename, original_model_id)
    print(mojo_model_from_file.model_id)
    assert_equals(mojo_model_from_file.model_id, original_model_id, "Ids should be the same.")

    # Test do not initialize model_id from original model (we don't overwrite the original model)
    mojo_model_up_wid = h2o.upload_mojo(original_model_filename)
    print(mojo_model_up_wid.model_id)
    assert_not_equal(mojo_model_up_wid.model_id, original_model_id, "Ids should not be the same.")

    mojo_model_im_wid = h2o.import_mojo(original_model_filename)
    print(mojo_model_im_wid.model_id)
    assert_not_equal(mojo_model_im_wid.model_id, original_model_id, "Ids should not be the same.")
    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_mojo_ids)
else:
    test_mojo_ids()
