#! /usr/env/python

import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o
from pandas.util.testing import assert_frame_equal


def download_data():
    results_dir = pyunit_utils.locate("results")
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    prostate_path = h2o.download_csv(prostate, os.path.join(results_dir, "prostate.csv"))
    assert prostate_path == os.path.join(results_dir, "prostate.csv")

    prostate2 = h2o.import_file(path=prostate_path)
    assert_frame_equal(prostate.as_data_frame(), prostate2.as_data_frame())


if __name__ == "__main__":
    pyunit_utils.standalone_test(download_data)
else:
    download_data()
