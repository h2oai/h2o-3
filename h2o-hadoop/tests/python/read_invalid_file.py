#! /usr/env/python

import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from pandas.util.testing import assert_frame_equal


def read_invalid_file():
  h2o.connect()
  try:
      hdfs_path = 'hdfs:///user/h2o/tests/invalid'
      hdfs_frame = h2o.import_file(hdfs_path)
      assert False, "Read of file, which does not exists was sucessfull. This is impossible"
  except ValueError as e:
      pass

if __name__ == '__main__':
  read_invalid_file()
