import sys
sys.path.insert(1,"../../")
from tests import pyunit_utils

# if datatable or polars/pyarrow is installed, this test will show that using datatable to convert h2o frame to pandas
# frame is much faster for large datasets.
def test_polars_datatable_2_pandas():
    file1 = "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/PUBDEV_5266_f1.csv"
    original_converted_frame1 = pyunit_utils.single_thread_pandas_conversion(file1)  # need to run conversion in single thread

    pyunit_utils.test_frame_conversion(file1, original_converted_frame1)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_polars_datatable_2_pandas)
else:
    test_polars_datatable_2_pandas()
