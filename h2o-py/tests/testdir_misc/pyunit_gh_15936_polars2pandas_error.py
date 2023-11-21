import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.utils.shared_utils import (can_install_polars, can_install_pyarrow, can_use_polars, can_use_pyarrow, can_use_datatable)
import time
import pandas as pd

# polars/pyarrows have problems before with this dataset.  Checking here to make sure it works.
def test_frame_conversion(h2oFrame, original_pandas_frame):
    print("h2o frame to pandas frame conversion using polars and pyarrow")
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
            
def test_polars_datatable():
    if can_install_polars() and can_install_pyarrow():
        datatable_present = False
        polars_present = False
    
        if can_use_datatable(): # remove in order to force to use polars/pyarrow
            datatable_present = True
            pyunit_utils.uninstall("datatable")
        if can_use_polars(): # force the use of single-thread original implementation
            polars_present = True
            pyunit_utils.uninstall("polars")

        h2oframe = genFrame()
        print("converting h2o frame to pandas frame using single thread:")
        original_pandas = h2oframe.as_data_frame()

        if not(can_use_polars()):
            pyunit_utils.install('polars')
        if not(can_use_pyarrow()):
            pyunit_utils.install('pyarrow')
        
        test_frame_conversion(h2oframe, original_pandas)
        
        if datatable_present:
            pyunit_utils.install("datatable")
        if polars_present:
            pyunit_utils.install("polars")
    else:
        print("polars and pyarrow are not available to test.  Skipping tests using polars and pyarrow.")

def genFrame():
    python_lists = [["ls 1029551"], ["no 983196"], ["true 689851"], ["437594"], ["no,ls 113569"], ["no,true 70607"]]
    col_names=["X"]
    col_types=['enum']
    return h2o.H2OFrame(python_obj=python_lists, column_names=col_names, column_types=col_types)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_polars_datatable)
else:
    test_polars_datatable()
