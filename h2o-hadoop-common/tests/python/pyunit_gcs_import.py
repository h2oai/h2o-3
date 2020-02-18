#! /usr/env/python

import sys
import os

sys.path.insert(1, os.path.join("../../../h2o-py"))
from tests import pyunit_utils
import h2o


def gcs_import():
    # Just test the import works - no class clashes, no exception
    keys = h2o.import_file(
        "gs://gcp-public-data-nexrad-l2/2018/01/01/KABR/NWS_NEXRAD_NXL2DPBL_KABR_20180101050000_20180101055959.tar",
        parse=False)
    assert len(keys) == 1
    assert keys == [
        'gs://gcp-public-data-nexrad-l2/2018/01/01/KABR/NWS_NEXRAD_NXL2DPBL_KABR_20180101050000_20180101055959.tar']

    expected_keys = [
        'gs://gcp-public-data-nexrad-l2/1991/06/05/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910605160000_19910605235959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/06/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910606000000_19910606075959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/06/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910606080000_19910606155959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/06/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910606160000_19910606235959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/07/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910607160000_19910607235959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/08/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910608000000_19910608075959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/08/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910608080000_19910608155959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/09/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910609160000_19910609235959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/10/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910610000000_19910610075959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/10/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910610080000_19910610155959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/22/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910622160000_19910622235959.tar',
        'gs://gcp-public-data-nexrad-l2/1991/06/23/KTLX/NWS_NEXRAD_NXL2LG_KTLX_19910623000000_19910623075959.tar']
    # Import folder
    keys = h2o.import_file("gs://gcp-public-data-nexrad-l2/1991/06", parse=False)
    assert len(keys) == 12
    assert keys == expected_keys

    # Import folder - slash at the end of path 
    keys = h2o.import_file("gs://gcp-public-data-nexrad-l2/1991/06/", parse=False)
    assert len(keys) == 12
    assert keys == expected_keys

    # Import folder - Invalid path
    keys = h2o.import_file("gs://gcp-public-data-nexrad-l2/1991/06/somethingNonExistent/", parse=False)
    assert len(keys) == 0


if __name__ == "__main__":
    pyunit_utils.standalone_test(gcs_import)
else:
    gcs_import()
