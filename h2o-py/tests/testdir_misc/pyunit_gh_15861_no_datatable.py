import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.utils.shared_utils import (can_use_datatable)
import time

# here, we delete the data table and make sure the code will run.
def test_frame_conversion(dataset, compareTime):
    print("Performing data conversion to pandas for dataset: {0}".format(dataset))
    h2oFrame = h2o.import_file(pyunit_utils.locate(dataset))
     # convert frame in single thread
    startT = time.time()
    original_pandas_frame = h2oFrame.as_data_frame()
    oldTime = time.time()-startT
    print("H2O frame to Pandas frame conversion time: {0}".format(oldTime))
    # convert frame using datatable
    startT = time.time()
    new_pandas_frame = h2oFrame.as_data_frame(multi_thread=True)
    newTime = time.time()-startT
    print("H2O frame to Pandas frame conversion time using datatable: {0}".format(newTime))
    if compareTime: # disable for small dataset.  Multi-thread can be slower due to more overhead
        assert newTime <= oldTime, " original frame conversion time: {0} should exceed new frame conversion time:" \
                                   "{1} but is not.".format(oldTime, newTime)
    # compare two frames column types                
    new_types = new_pandas_frame.dtypes
    old_types = original_pandas_frame.dtypes
    ncol = h2oFrame.ncol
    
    for ind in range(ncol):
        assert new_types[ind] == old_types[ind], "Expected column types: {0}, actual column types: " \
                                                 "{1}".format(old_types[ind], new_types[ind])
    

def test_datatable_without_datatable():
    delTable = False
    if can_use_datatable():
        delTable = True
        pyunit_utils.uninstall("datatable")
        
    # should run to completion
    with pyunit_utils.catch_warnings() as ws:
        test_frame_conversion("bigdata/laptop/jira/PUBDEV_5266_merge_with_string_columns/PUBDEV_5266_f1.csv", False)
        assert "multi_thread mode can only be used when you have datatable installed.  Default to single-thread " \
               "operation." in str(ws[0].message)
        
    # re-install datatable before quitting.     
    if delTable:
        pyunit_utils.install("datatable")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_datatable_without_datatable)
else:
    test_datatable_without_datatable()
