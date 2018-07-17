import pandas as pd
import xgboost as xgb
from sklearn.preprocessing import LabelEncoder

from h2o.estimators.xgboost import *
from tests import pyunit_utils


def xgboost_airlines_all_multinode_large():
    assert H2OXGBoostEstimator.available() is True
    ret = h2o.cluster()
    if len(ret.nodes) == 1:
        frame = h2o.import_file(path=pyunit_utils.locate("bigdata/server/airlines_all.csv"))

        # The ntrees parameters in H2O translates to max_depth param
        h2o_model = H2OXGBoostEstimator(training_frame=frame, learn_rate = 0.7,
                                        booster='gbtree', seed=1, ntrees=5, dmatrix_type='sparse')
        # Categorical variables only - sparse dataset
        h2o_model.train(x=['Year','Month', 'DayofMonth', 'DayOfWeek', 'DepTime', 'CRSDepTime', 'ArrTime',
                           'CRSArrTime','UniqueCarrier', 'FlightNum', 'TailNum','ActualElapsedTime', 'CRSElapsedTime', 'AirTime',
                           'ArrDelay', 'DepDelay', 'Distance', 'TaxiIn', 'TaxiOut', 'Cancelled','CancellationCode','Diverted',
                           'CarrierDelay', 'WeatherDelay', 'NASDelay', 'SecurityDelay', 'LateAircraftDelay', 'IsArrDelayed',
                           'Origin', 'Dest'], y='IsDepDelayed', training_frame=frame)
    else:
        print("********  Test skipped.  This test cannot be performed in multinode environment.")
if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_airlines_all_multinode_large)
else:
    xgboost_airlines_all_multinode_large()
