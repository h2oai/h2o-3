#! /usr/env/python

import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
from tests import pyunit_utils
import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator


def gbm_on_hadoop():
  local_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  hdfs_path = 'hdfs:///user/jenkins/tests/prostate_export'
  h2o.export_file(local_frame, hdfs_path, force=True)
  df = h2o.import_file(hdfs_path)
  train = df.drop("ID")
  vol = train['VOL']
  vol[vol == 0] = None
  gle = train['GLEASON']
  gle[gle == 0] = None
  train['CAPSULE'] = train['CAPSULE'].asfactor()
  my_gbm = H2OGradientBoostingEstimator(ntrees=50,
                                        learn_rate=0.1,
                                        distribution="bernoulli")
  my_gbm.train(x=list(range(1, train.ncol)),
               y="CAPSULE",
               training_frame=train,
               validation_frame=train)
  my_gbm.predict(train)

if __name__ == "__main__":
    pyunit_utils.standalone_test(gbm_on_hadoop)
else:
    gbm_on_hadoop()