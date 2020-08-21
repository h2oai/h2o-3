#! /usr/env/python

import sys
import os
sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o


def download_logs():
    results_dir = pyunit_utils.locate("results")

    logs_path = h2o.download_all_logs()
    assert os.path.exists(logs_path)

    logs_path_explicit = h2o.download_all_logs(dirname=results_dir, filename="logs.zip")
    assert logs_path_explicit == os.path.join(results_dir, "logs.zip")
    assert os.path.exists(logs_path_explicit)

if __name__ == "__main__":
    pyunit_utils.standalone_test(download_logs)
else:
    download_logs()
