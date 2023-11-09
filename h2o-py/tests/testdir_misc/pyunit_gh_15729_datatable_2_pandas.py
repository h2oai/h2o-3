import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.utils.shared_utils import (can_install_datatable, can_use_datatable)
import time
import numpy as np
import pandas as pd

# if datatable is installed, this test will show that using datatable to convert h2o frame to pandas frame is
# much faster for large datasets.
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
    nrow = h2oFrame.nrow
    
    for ind in list(range(ncol)):
        assert new_types[ind] == old_types[ind], "Expected column types: {0}, actual column types: " \
                                                 "{1}".format(old_types[ind], new_types[ind])
        if new_types[ind] == "object":
            diff = new_pandas_frame.iloc[:, ind] == original_pandas_frame.iloc[:, ind]
            if not diff.all(): # difference caused by the presence of NAs
                newSeries = pd.Series(new_pandas_frame.iloc[:, ind])
                newNA = newSeries.isna()
                oldSeries = pd.Series(original_pandas_frame.iloc[:, ind])
                oldNA = oldSeries.isna()
                assert (newNA==oldNA).all()       
        else:
            diff = (new_pandas_frame.iloc[:, ind] - original_pandas_frame.iloc[:, ind]).abs()
            assert diff.max() < 1e-10
            
def test_polars_pandas():
    if not can_install_datatable():
        print("Datatable doesn't run on Python 3.{0} for now.".format(sys.version_info.minor))
        return
    if not(can_use_datatable()):
        pyunit_utils.install("datatable")
    import datatable
    test_frame_conversion("smalldata/titanic/titanic_expanded.csv", False)
    test_frame_conversion("smalldata/glm_test/multinomial_3Class_10KRow.csv", False)
    test_frame_conversion("smalldata/timeSeries/CreditCard-ts_train.csv", False)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_polars_pandas)
else:
    test_polars_pandas()
