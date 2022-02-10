#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
import h2o
from tests import pyunit_utils
from pandas.testing import assert_frame_equal


def test_import_from_long_urls():
    prostate_path = pyunit_utils.locate("smalldata/logreg/prostate.csv")
    prostate = h2o.import_file(path=prostate_path)

    padding = "x" * 512

    # get the data from H2O to avoid making calls to public internet
    conn = h2o.connection()
    url = conn._base_url + "/3/DownloadDataset?frame_id=%s&hex_string=false&padding=" % prostate.frame_id + padding

    prostate_from_self = h2o.import_file(url)

    assert_frame_equal(prostate_from_self.as_data_frame(), prostate.as_data_frame())
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_import_from_long_urls)
else:
    test_import_from_long_urls()
