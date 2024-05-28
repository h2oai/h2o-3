import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# multi-thread conversion have problems before with this dataset.  Checking here to make sure it works.            
def test_multi_thread():
    if sys.version_info.major == 3 and sys.version_info.minor <= 9: # use datatable
        package = 'datatable'
    else:
        package = 'pyarrow and polars'
    print("h2o frame to pandas frame conversion using {0}.\n".format(package))
    h2oframe = genFrame()
    original_pandas = h2oframe.as_data_frame()
    pyunit_utils.test_frames_conversion(h2oframe, original_pandas, package)

def genFrame():
    python_lists = [["ls 1029551"], ["no 983196"], ["true 689851"], ["437594"], ["no,ls 113569"], ["no,true 70607"]]
    col_names=["X"]
    col_types=['enum']
    return h2o.H2OFrame(python_obj=python_lists, column_names=col_names, column_types=col_types)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_multi_thread)
else:
    test_multi_thread()
