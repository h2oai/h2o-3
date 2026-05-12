from tests import pyunit_utils
import h2o
import time
import pandas as pd

def test_frame_conversion(dataset, original_pandas_frame):
    # convert frame using datatable or polar/pyarrow
    h2oframe = h2o.import_file(pyunit_utils.locate(dataset))
    test_frames_conversion(h2oframe, original_pandas_frame)
            
def test_frames_conversion(h2oframe, original_pandas_frame):
    start_time = time.time()
    new_pandas_frame = h2oframe.as_data_frame(use_multi_thread=True)
    new_time = time.time()-start_time
    print("H2O frame to Pandas frame conversion time with multi-thread using module polars/pyarrow: {0}".format(new_time))
    # compare two frames column types                
    new_types = new_pandas_frame.dtypes
    old_types = original_pandas_frame.dtypes
    ncol = h2oframe.ncol
    col_names = new_pandas_frame.columns

    for ind in list(range(ncol)):
        assert new_types[col_names[ind]] == old_types[col_names[ind]], "Expected column types: {0}, actual column types: " \
                                                                     "{1}".format(old_types[col_names[ind]], new_types[col_names[ind]])
        if new_types[col_names[ind]] == "object":
            diff = new_pandas_frame[col_names[ind]] == original_pandas_frame[col_names[ind]]
            if not diff.all(): # difference caused by the presence of NAs
                new_series = pd.Series(new_pandas_frame[col_names[ind]])
                new_NA = new_series.isna()
                old_series = pd.Series(original_pandas_frame[col_names[ind]])
                old_NA = old_series.isna()
                assert (new_NA==old_NA).all()
        else:
            diff = (new_pandas_frame[col_names[ind]] - original_pandas_frame[col_names[ind]]).abs()
            assert diff.max() < 1e-10    


def single_thread_pandas_conversion(dataset):
    print("converting h2o frame to pandas frame using single thread")
    h2oframe = h2o.import_file(pyunit_utils.locate(dataset))
    start_time = time.time()
    h2oframe_panda =  h2oframe.as_data_frame()
    new_time = time.time()-start_time
    print("H2O frame to Pandas frame conversion time with single thread for dataset {1}: {0}".format(new_time, dataset))
    return h2oframe_panda
