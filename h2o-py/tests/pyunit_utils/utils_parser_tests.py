from tests import pyunit_utils
import h2o
import time
import pandas as pd

def test_frame_conversion(dataset, original_pandas_frame, module):
    # convert frame using datatable or polar/pyarrow
    h2oFrame = h2o.import_file(pyunit_utils.locate(dataset))
    test_frames_conversion(h2oFrame, original_pandas_frame, module)
            
def test_frames_conversion(h2oFrame, original_pandas_frame, module):
    startT = time.time()
    new_pandas_frame = h2oFrame.as_data_frame(use_multi_thread=True)
    newTime = time.time()-startT
    print("H2O frame to Pandas frame conversion time with multi-thread using module {1}: {0}".format(newTime, module))
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
    print("converting h2o frame to pandas frame using single thread")
    h2oFrame = h2o.import_file(pyunit_utils.locate(dataset))
    startT = time.time()
    h2oframe_panda =  h2oFrame.as_data_frame()
    newTime = time.time()-startT
    print("H2O frame to Pandas frame conversion time with single thread for dataset {1}: {0}".format(newTime, dataset))
    return h2oframe_panda
