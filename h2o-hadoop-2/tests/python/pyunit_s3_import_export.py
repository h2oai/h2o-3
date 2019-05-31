#! /usr/env/python

import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
from tests import pyunit_utils
from datetime import datetime
import h2o
import uuid
from pandas.util.testing import assert_frame_equal

def s3_import_export():
    local_frame = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    for scheme in ["s3n", "s3a"]:
        timestamp = datetime.today().utcnow().strftime("%Y%m%d-%H%M%S")
        unique_suffix = str(uuid.uuid4())
        s3_path = scheme + "://test.0xdata.com/h2o-hadoop-tests/test-export/" + scheme + "/exported." + \
                  timestamp + "." + unique_suffix + ".csv.zip"
        h2o.export_file(local_frame, s3_path)
        s3_frame = h2o.import_file(s3_path)
        assert_frame_equal(local_frame.as_data_frame(), s3_frame.as_data_frame())

if __name__ == "__main__":
    pyunit_utils.standalone_test(s3_import_export)
else:
    s3_import_export()
