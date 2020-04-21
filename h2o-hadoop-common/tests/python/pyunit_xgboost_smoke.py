#! /usr/env/python
import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o
from h2o.estimators import H2OXGBoostEstimator

def xgb_smoke():
  df = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  train = df.drop("ID")
  vol = train['VOL']
  vol[vol == 0] = None
  gle = train['GLEASON']
  gle[gle == 0] = None
  train['CAPSULE'] = train['CAPSULE'].asfactor()
  xgb = H2OXGBoostEstimator(ntrees=10, learn_rate=0.1)
  xgb.train(x=list(range(1, train.ncol)), y="CAPSULE", training_frame=train)
  xgb.predict(train)

if __name__ == "__main__":
    pyunit_utils.standalone_test(xgb_smoke)
else:
    xgb_smoke()
