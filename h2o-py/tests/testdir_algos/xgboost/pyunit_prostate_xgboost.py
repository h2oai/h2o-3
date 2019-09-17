from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.xgboost import H2OXGBoostEstimator

def prostate_xgboost():
  df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

  # Remove ID from training frame
  train = df.drop("ID")

  # For VOL & GLEASON, a zero really means "missing"
  vol = train['VOL']
  vol[vol == 0] = None
  gle = train['GLEASON']
  gle[gle == 0] = None

  # Convert CAPSULE to a logical factor
  train['CAPSULE'] = train['CAPSULE'].asfactor()

  # Run XGBoost
  model = H2OXGBoostEstimator(ntrees=5, learn_rate=0.1)
  model.train(x=list(range(1, train.ncol)),
               y="CAPSULE",
               training_frame=train,
               validation_frame=train)

  p = model.predict(train)
  p.describe()
  assert p.nrow == train.nrow

  ln = model.predict_leaf_node_assignment(train)
  ln.describe()
  assert ln.names == ['T1', 'T2', 'T3', 'T4', 'T5']
  assert ln.nrow == train.nrow

  lnids = model.predict_leaf_node_assignment(train, type="Node_ID")
  lnids.describe()
  assert lnids.names == ['T1', 'T2', 'T3', 'T4', 'T5']
  assert lnids.nrow == train.nrow

if __name__ == "__main__":
  pyunit_utils.standalone_test(prostate_xgboost)
else:
  prostate_xgboost()
