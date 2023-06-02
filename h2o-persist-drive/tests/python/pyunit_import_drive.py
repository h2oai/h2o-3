#! /usr/env/python

import sys, os
sys.path.insert(1, os.path.join("..", "..", "..", "h2o-py"))
from tests import pyunit_utils
import h2o
from pandas.util.testing import assert_frame_equal


def test_drive_import():
    local_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    drive_frame = h2o.import_file("drive://h2o-public-test-data/smalldata/logreg/prostate.csv")
    assert_frame_equal(local_frame.as_data_frame(), drive_frame.as_data_frame())

    resp = h2o.api("GET /3/Typeahead/files?src=drive://h2o-public-test-data/smalldata/logre&limit=3")
    assert len(resp["matches"]) == 3


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_drive_import)
else:
    test_drive_import()
