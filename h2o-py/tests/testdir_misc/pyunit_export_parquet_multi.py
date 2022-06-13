from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import string
import os
import glob
import random
from pandas.util.testing import assert_frame_equal


def export_parquet_multipart():
    test_cases = {"smalldata/prostate/prostate.csv", # to check nums and categories
                  "smalldata/titanic/titanic_expanded.csv", # to check nums and categories
                  "smalldata/testng/airquality_train1.csv", # to check NA
                  "smalldata/gbm_test/autoclaims.csv", # to check datetime
                  "smalldata/demos/item_demand.csv"} # to check datetime + more chunks

    for case in test_cases:
        print("Testing parquet export on " + case)
        test_export_import_parquet(case)


def test_export_import_parquet(testdata):
    data = h2o.upload_file(pyunit_utils.locate(testdata))
    path = pyunit_utils.locate("results")
    dname = os.path.join(path, id_generator() + "_parquet_export_results")

    h2o.export_file(data, dname, format="parquet")
    assert os.path.isdir(dname)

    imported_file = h2o.import_file(dname, "part-m-")

    part_files = glob.glob(os.path.join(dname, "part-m-?????"))
    if len(part_files) == 1:
        # 'check_column_type=False' because all numeric types are exported as doubles
        assert_frame_equal(imported_file.as_data_frame(True), data.as_data_frame(True), check_column_type=False)
    else:
        pd_frame = data.as_data_frame(True)
        imported_file = imported_file.as_data_frame(True)
        assert imported_file.shape == pd_frame.shape
        assert all(imported_file.columns == pd_frame.columns)
        assert all(pd_frame.mean() == imported_file.mean())
        assert all(pd_frame.max() == imported_file.max())


def id_generator(size=6, chars=string.ascii_uppercase + string.digits):
    return ''.join(random.choice(chars) for _ in range(size))


if __name__ == "__main__":
    pyunit_utils.standalone_test(export_parquet_multipart)
else:
    export_parquet_multipart()



