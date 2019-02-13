import h2o
import tempfile
from h2o.estimators import H2OXGBoostEstimator, H2OMojodelegatingEstimator
from tests import pyunit_utils


def mojo_model_xgboost_test():

    # XGBoost
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    xgboost = H2OXGBoostEstimator(ntrees=1) # fastest test possible, no need for accuracy
    xgboost.train(x = ["Origin", "Dest"], y = "IsDepDelayed", training_frame=airlines)

    filename = tempfile.mkdtemp()
    filename = xgboost.download_mojo(filename)
      
    model = H2OMojodelegatingEstimator.from_mojo_file(filename)
    assert model is not None
    predictions = model.predict(airlines)
    assert predictions is not None
    assert predictions.nrows == 24421
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(mojo_model_xgboost_test)
else:
    mojo_model_xgboost_test()
