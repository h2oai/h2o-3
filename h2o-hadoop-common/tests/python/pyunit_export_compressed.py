#! /usr/env/python

import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o


def smoke_export_compressed():
    local_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    hdfs_path = 'hdfs:///user/jenkins/tests/prostate_export_compressed.csv.%s'
    h2o.export_file(local_frame, hdfs_path % "gz", force=True, compression="gzip")
    h2o.export_file(local_frame, hdfs_path % "bz2", force=True, compression="bzip2")
    h2o.export_file(local_frame, hdfs_path % "snappy", force=True, compression="snappy")


if __name__ == "__main__":
    pyunit_utils.standalone_test(smoke_export_compressed)
else:
    smoke_export_compressed()
