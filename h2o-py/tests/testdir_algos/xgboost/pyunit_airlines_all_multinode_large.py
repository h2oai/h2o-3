import pandas as pd
import xgboost as xgb
from sklearn.preprocessing import LabelEncoder

from h2o.estimators.xgboost import *
from tests import pyunit_utils


def multinode():
    assert H2OXGBoostEstimator.available() is True

    train = h2o.import_file(path=pyunit_utils.locate("bigdata/server/airlines_all.csv"))

    # H2O XGBoost model trained
    frame = h2o.H2OFrame(train)
    # Force factor variables, even if recognized correctly
        # The ntrees parameters in H2O translates to max_depth param
    h2o_model = H2OXGBoostEstimator(training_frame=frame, learn_rate = 0.7,
                                booster='gbtree', seed=1, ntrees=5)
    h2o_model.train(x=['Origin', 'Dest'], y='IsDepDelayed', training_frame=frame)

    h2o_model.summary()

if __name__ == "__main__":
    pyunit_utils.standalone_test(multinode)
else:
    multinode()
