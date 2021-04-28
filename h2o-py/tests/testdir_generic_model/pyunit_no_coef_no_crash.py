import h2o
from h2o.estimators import H2OGradientBoostingEstimator
from tests import pyunit_utils


def calling_coef_doesnt_crash_on_weird_error():
    airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    model = H2OGradientBoostingEstimator(ntrees=5)
    model.train(x=["Origin"], y="IsDepDelayed", training_frame=airlines)
        
    assert model.coef_norm() is None
    assert model.coef() is None
    # just test it doesn't crash
    model.pprint_coef()

    
if __name__ == "__main__":
    pyunit_utils.standalone_test(calling_coef_doesnt_crash_on_weird_error)
else:
    calling_coef_doesnt_crash_on_weird_error()
