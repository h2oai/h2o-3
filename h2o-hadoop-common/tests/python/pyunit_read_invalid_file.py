#! /usr/env/python
import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o


def read_invalid_file():
    try:
        hdfs_path = 'hdfs:///user/jenkins/tests/invalid'
        h2o.import_file(hdfs_path)
        assert False, "Read of file, which does not exists was sucessfull. This is impossible"
    except ValueError as ve:
        print(ve)
        pass


if __name__ == "__main__":
    pyunit_utils.standalone_test(read_invalid_file)
else:
    read_invalid_file()
