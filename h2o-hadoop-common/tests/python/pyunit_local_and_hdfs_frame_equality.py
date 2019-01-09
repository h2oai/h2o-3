#! /usr/env/python

import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
from tests import pyunit_utils
import h2o
from pandas.util.testing import assert_frame_equal


def local_and_hdfs_frame_equality():
  local_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  hdfs_path = 'hdfs:///user/jenkins/tests/prostate_export'
  h2o.export_file(local_frame, hdfs_path, force=True)
  hdfs_frame = h2o.import_file(hdfs_path)
  assert_frame_equal(local_frame.as_data_frame(), hdfs_frame.as_data_frame())

if __name__ == "__main__":
  pyunit_utils.standalone_test(local_and_hdfs_frame_equality)
else:
  local_and_hdfs_frame_equality()