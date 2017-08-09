#! /usr/env/python

import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from pandas.util.testing import assert_frame_equal


def local_and_hdfs_frame_equality():
  h2o.connect()
  local_frame = h2o.import_file('https://h2o-public-test-data.s3.amazonaws.com/smalldata/logreg/prostate.csv')
  hdfs_path = 'hdfs:///user/h2o/tests/prostate_export'
  h2o.export_file(local_frame, hdfs_path, force=True)
  hdfs_frame = h2o.import_file(hdfs_path)
  assert_frame_equal(local_frame.as_data_frame(), hdfs_frame.as_data_frame())

if __name__ == '__main__':
  local_and_hdfs_frame_equality()
