from builtins import range
import sys, os

from h2o.estimators import H2OGenericEstimator

sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import tempfile

def pubdev_8309():

    airlines = h2o.upload_file(pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))
    
    # convert columns to factors
    airlines["Year"]= airlines["Year"].asfactor()
    airlines["Month"]= airlines["Month"].asfactor()
    airlines["DayOfWeek"] = airlines["DayOfWeek"].asfactor()
    airlines["Cancelled"] = airlines["Cancelled"].asfactor()
    airlines['FlightNum'] = airlines['FlightNum'].asfactor()
    
    # set the predictor names and the response column name
    predictors = airlines.columns[:9]
    response = "IsDepDelayed"
    
    # split into train and validation sets
    train, valid= airlines.split_frame(ratios=[.8], seed=1234)
    
    # create a list of column names to ignore
    col_list = ['ArrTime','DepTime','CRSArrTime','CRSDepTime']
    
    # initialize the estimator and train the model
    airlines_gbm = H2OGradientBoostingEstimator(ignored_columns=col_list, seed=1234)
    
    airlines_gbm.train(y=response, training_frame=train, validation_frame=valid)

    original_model_filename = tempfile.mkdtemp()
    # Download the MOJO
    original_model_filename = airlines_gbm.download_mojo(original_model_filename)
    # Load the model from the temporary file
    mojo_model = h2o.import_mojo(original_model_filename)
    assert isinstance(mojo_model, H2OGenericEstimator)
    assert mojo_model.params["ignored_columns"] == airlines_gbm.params["ignored_columns"]
    mojo_model.params["ignored_columns"]['actual'] == col_list


if __name__ == "__main__":
  pyunit_utils.standalone_test(pubdev_8309)
else:
    pubdev_8309()
