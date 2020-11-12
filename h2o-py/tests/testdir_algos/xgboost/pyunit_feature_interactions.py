from h2o.estimators.xgboost import *
from tests import pyunit_utils
import sys
sys.path.insert(1,"../../../")
from h2o.two_dim_table import H2OTwoDimTable


def xgboost_feature_interactions():
    prostate_frame = h2o.import_file(pyunit_utils.locate('smalldata/prostate/prostate.csv'))
    y = "RACE"
    ignored_columns = ['ID']

    model = H2OXGBoostEstimator(training_frame=prostate_frame, seed=0xDECAF)
    model.train(y=y, ignored_columns=ignored_columns, training_frame=prostate_frame)
  
    feature_interactions = model.feature_interaction(2, 100, -1)
    assert isinstance(feature_interactions[0], H2OTwoDimTable)
    assert len(feature_interactions) == 11
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(xgboost_feature_interactions)
else:
    xgboost_feature_interactions()
