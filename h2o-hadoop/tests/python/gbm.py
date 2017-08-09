#! /usr/env/python

import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from pandas.util.testing import assert_frame_equal


def local_and_hdfs_frame_equality():
  h2o.connect()
  local_frame = h2o.import_file('https://h2o-public-test-data.s3.amazonaws.com/smalldata/logreg/prostate.csv')
  hdfs_path = 'hdfs:///user/h2o/tests/prostate_export'
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
  p = my_gbm.predict(train)

if __name__ == '__main__':
  local_and_hdfs_frame_equality()
