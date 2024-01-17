import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.utils.shared_utils import (can_use_datatable, can_install_datatable)
import pandas as pd
from h2o.utils.threading import local_context


# datatable have problems before with this dataset.  Checking here to make sure it works.
def test_frame_conversion(h2oFrame, original_pandas_frame):
    print("h2o frame to pandas frame conversion using datatable")
    new_pandas_frame = h2oFrame.as_data_frame()
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
            
            
def test_datatable():
    if can_install_datatable():
        if not(can_use_datatable()):
            pyunit_utils.install("datatable")
        
        with local_context(datatable_disabled=True, polars_disabled=True):
            h2oframe = genFrame()
            print("converting h2o frame to pandas frame using single thread:")
            original_pandas = h2oframe.as_data_frame()
        with local_context(polars_disabled=True):
            test_frame_conversion(h2oframe, original_pandas)  
    else:
        print("datatable are not available to test.  Skipping tests using datatable.")


def genFrame():
    python_lists = [["ls 1029551"], ["no 983196"], ["true 689851"], ["437594"], ["no,ls 113569"], ["no,true 70607"]]
    col_names=["X"]
    col_types=['enum']
    return h2o.H2OFrame(python_obj=python_lists, column_names=col_names, column_types=col_types)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_datatable)
else:
    test_datatable()
