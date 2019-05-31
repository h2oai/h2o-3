#! /usr/env/python

import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
from tests import pyunit_utils
import h2o

def gcs_import():
    # Just test the import works - no class clashes, no exception
    fr = h2o.import_file("gs://gcp-public-data-nexrad-l2/2018/01/01/KABR/NWS_NEXRAD_NXL2DPBL_KABR_20180101050000_20180101055959.tar", parse=False)


if __name__ == "__main__":
    pyunit_utils.standalone_test(gcs_import)
else:
    gcs_import()
