import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.utils.shared_utils import (can_install_datatable, can_use_datatable, can_install_polars, can_install_pyarrow,
                                    can_use_polars, can_use_pyarrow)
import time
import pandas as pd

# if datatable or polars/pyarrow is installed, this test will show that using datatable to convert h2o frame to pandas
# frame is much faster for large datasets.
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
            
def H2O2PandasSingleThread(dataset):
    print("converting h2o frame to pandas frame using single thread")
    h2oFrame = h2o.import_file(pyunit_utils.locate(dataset))
    startT = time.time()
    h2oframe_panda =  h2oFrame.as_data_frame()
    newTime = time.time()-startT
    print("H2O frame to Pandas frame conversion time with single thread for dataset {1}: {0}".format(newTime, dataset))
    return h2oframe_panda
    
def test_polars_datatable(): 
    file1 = "smalldata/titanic/titanic_expanded.csv"
    file2 = "smalldata/glm_test/multinomial_3Class_10KRow.csv"
    file3 = "smalldata/timeSeries/CreditCard-ts_train.csv"
    datatable_present = False
    polars_present = False
    pyarrow_present = False

    if can_install_datatable() or (can_install_polars() and can_install_pyarrow()):    
        # need to run conversion in single thread
        if can_install_polars():
            if can_use_polars():
                pyunit_utils.uninstall("polars")
        if can_install_datatable():
            if can_use_datatable(): 
                pyunit_utils.uninstall("datatable")
        original_converted_frame1 = H2O2PandasSingleThread(file1)
        original_converted_frame2 = H2O2PandasSingleThread(file2)
        original_converted_frame3 = H2O2PandasSingleThread(file3)
        
        if can_install_datatable():
            if not(can_use_datatable()):
                pyunit_utils.install("datatable")
            else:
                datatable_present = True
            print("test data frame conversion using datatable.")
            test_frame_conversion(file1, original_converted_frame1, "datatable")
            test_frame_conversion(file2, original_converted_frame2, "datatable")
            test_frame_conversion(file3, original_converted_frame3, "datatable")
            pyunit_utils.uninstall("datatable")
        else:
            print("datatable is not available.  Skipping tests using datatable.")
        

        if can_install_polars() and can_install_pyarrow():
            if not(can_use_polars()):
                pyunit_utils.install('polars')
            else:
                polars_present = True
            if not(can_use_pyarrow()):
                pyunit_utils.install('pyarrow')
            else:
                pyarrow_present = True
            print("test data frame conversion using polars and pyarrow.")
            test_frame_conversion(file1, original_converted_frame1, "polars and pyarrow")
            test_frame_conversion(file2, original_converted_frame2, "polars and pyarrow")
            test_frame_conversion(file3, original_converted_frame3, "polars and pyarrow")     
        else:
            print("polars, pyarrow are not available.  Skipping tests using polars and pyarrow")
        
        # leave test environment unchanged
        if datatable_present:
            pyunit_utils.install("datatable")
        if polars_present:
            pyunit_utils.install("polars")
        if pyarrow_present:
            pyunit_utils.install("pyarrow")
                
    else:
        print("datatable or polars and pyarrow are not available to test.  Skipping tests using polars and pyarrow.")



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_polars_datatable)
else:
    test_polars_datatable()
