import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator

def retain_keys_test():
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    gbm = H2OGradientBoostingEstimator(ntrees = 1)
    gbm.train(x = ["Origin", "Dest"], y = "IsDepDelayed", training_frame=airlines)
    
    h2o.remove_all([airlines.frame_id, gbm.model_id])
    
    assert h2o.get_frame(airlines.frame_id) is not None
    assert h2o.get_model(gbm.model_id) is not None

    ## Test key not being retained when unspecified
    gbm = H2OGradientBoostingEstimator(ntrees = 1)
    gbm.train(x = ["Origin", "Dest"], y = "IsDepDelayed", training_frame=airlines)
    
    h2o.remove_all([airlines.frame_id])
    h2o.ls()
    try:
        h2o.get_model(gbm.model_id)
        assert False
    except h2o.exceptions.H2OResponseError as e:
        assert e.args[0].dev_msg.find ("not found for argument: key") != -1;

if __name__ == "__main__":
    pyunit_utils.standalone_test(retain_keys_test)
else:
    retain_keys_test()
