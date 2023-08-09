import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.utils.shared_utils import (can_use_pandas, can_use_polars, can_use_pyarrow)
import time
import subprocess

# if polars is installed, this test will show that using polars to convert h2o frame to pandas frame is
# much faster.
def test_frame_conversion(dataset, compareTime):
    print("Performing data conversion to pandas for dataset: {0}".format(dataset))
    h2oFrame = h2o.import_file(pyunit_utils.locate(dataset))
     # convert frame not using datatable
    startT = time.time()
    original_pandas_frame = h2oFrame.as_data_frame()
    oldTime = time.time()-startT
    print("H2O frame to Pandas frame conversion time: {0}".format(oldTime))
    # convert frame using datatable
    startT = time.time()
    new_pandas_frame = h2oFrame.as_data_frame(multi_thread=True)
    newTime = time.time()-startT
    print("H2O frame to Pandas frame conversion time using polars: {0}".format(newTime))
    if compareTime: # disable for small dataset.  Multi-thread can be slower due to more overhead
        assert newTime <= oldTime, " original frame conversion time: {0} should exceed new frame conversion time:" \
                                   "{1} but is not.".format(oldTime, newTime)
    # compare two frames column types                
    original_types = original_pandas_frame.dtypes
    new_types = new_pandas_frame.dtypes
    for ind in range(0, h2oFrame.ncols):
        assert str(original_types[ind])==str(new_types[ind]), \
            "Expected column {0} type: {1}, actual {2}.".format(ind, str(original_types[ind]), str(new_types[ind]))

def test_polars_pandas():
    if not(can_use_pandas()):
        pyunit_utils.install("pandas")
    import pandas
    print(pandas.__version__)
    if float(pandas.__version__[0]) >= 1:
        if not(can_use_polars()):
            pyunit_utils.install("polars")
        if not(can_use_pyarrow()):
            pyunit_utils.install("pyarrow")
        test_frame_conversion("smalldata/glm_test/multinomial_3Class_10KRow.csv", True)
        test_frame_conversion("smalldata/titanic/titanic_expanded.csv", False)
        test_frame_conversion("smalldata/timeSeries/CreditCard-ts_train.csv", False)
    else:
        print("Test skipped due to old pandas version")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_polars_pandas)
else:
    test_polars_pandas()
