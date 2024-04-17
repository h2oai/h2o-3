import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.utils.shared_utils import (can_use_datatable, can_use_polars, can_use_pyarrow, can_install_datatable, 
                                    can_install_polars)
import time
from h2o.utils.threading import local_context


def test_frame_conversion(dataset, original_pandas_frame, module):
    # convert frame using datatable or polar
    h2oFrame = h2o.import_file(pyunit_utils.locate(dataset))
    startT = time.time()
    new_pandas_frame = h2oFrame.as_data_frame()
    newTime = time.time()-startT
    print("H2O frame to Pandas frame conversion time with multi-thread using module {1} for dataset {2}: {0}".format(newTime, module, dataset))
    # compare two frames column types                
    new_types = new_pandas_frame.dtypes
    old_types = original_pandas_frame.dtypes
    ncol = h2oFrame.ncol
    colNames = new_pandas_frame.columns

    for ind in list(range(ncol)):
        assert new_types[colNames[ind]] == old_types[colNames[ind]], "Expected column types: {0}, actual column types: " \
                                                                     "{1}".format(old_types[colNames[ind]], new_types[colNames[ind]])
        if new_types[colNames[ind]] == "object":
            diff = new_pandas_frame[colNames[ind]] == original_pandas_frame[colNames[ind]]
            if not diff.all(): # difference caused by the presence of NAs
                newSeries = pd.Series(new_pandas_frame[colNames[ind]])
                newNA = newSeries.isna()
                oldSeries = pd.Series(original_pandas_frame[colNames[ind]])
                oldNA = oldSeries.isna()
                assert (newNA==oldNA).all()
        else:
            diff = (new_pandas_frame[colNames[ind]] - original_pandas_frame[colNames[ind]]).abs()
            assert diff.max() < 1e-10


def single_thread_pandas_conversion(dataset):
    with local_context(datatable_disabled=True, polars_disabled=True):
        print("converting h2o frame to pandas frame using single thread")
        h2oFrame = h2o.import_file(pyunit_utils.locate(dataset))
        startT = time.time()
        h2oframe_panda =  h2oFrame.as_data_frame()
        newTime = time.time()-startT
        print("H2O frame to Pandas frame conversion time with single thread for dataset {1}: {0}".format(newTime, dataset))
        return h2oframe_panda


# if datatable or polars/pyarrow is installed, this test will show that using datatable to convert h2o frame to pandas
# frame is much faster for large datasets.
def test_polars_datatable_2_pandas():
    file1 = "bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/PUBDEV_5266_f1.csv"
    original_converted_frame1 = single_thread_pandas_conversion(file1)  # need to run conversion in single thread

    if can_install_datatable():
        with local_context(polars_disabled=True, datatable_enabled=True):   # run with datatable
            assert can_use_datatable(), "Can't use datatable"
            print("test data frame conversion using datatable.")
            test_frame_conversion(file1, original_converted_frame1, "datatable")

    if can_install_polars():
        with local_context(datatable_disabled=True, polars_enabled=True):
            assert can_use_polars() and can_use_pyarrow(), "Can't use polars"
            print("test data frame conversion using polars and pyarrow.")
            test_frame_conversion(file1, original_converted_frame1, "polars and pyarrow")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_polars_datatable_2_pandas)
else:
    test_polars_datatable_2_pandas()
