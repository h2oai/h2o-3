from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def mergeOneEmptyFrame():
    # PUBDEV-6987: merge with one empty frame and one normal frame.
    file1 = h2o.H2OFrame({"A1":[1], "A2":[0]})
    file2 = h2o.H2OFrame({"A1":[], "A2":[]})
    # all_x = all_y = False, only merge rows that appear both it the right and left frames
    f1Mergef2 = file1.merge(file2) # right frame is empty, stall here
    f2Mergef1 = file2.merge(file1)  # left frame is empty, should return empty frame
    f2Mergef2 = file2.merge(file2)  # merging of empty frame with just headers

    # all three frames should have zero number of rows
    assert f1Mergef2.nrows == 0, "Expected empty rows but actual number of row is {0}!".format(f1Mergef2.nrows)
    assert f2Mergef1.nrows == 0, "Expected empty rows but actual number of row is {0}!".format(f2Mergef1.nrows)
    assert f2Mergef2.nrows == 0, "Expected empty rows but actual number of row is {0}!".format(f2Mergef2.nrows)   
    
    f1Mergef2 = file1.merge(file2, all_x=True) # should contain content of file1, merge everything in f1
    f2Mergef1 = file2.merge(file1, all_y=True) # should contain content of file1, merge everything in f2

    assert f1Mergef2.nrow == 1, "Expected one row  but actual number of row is {0}!".format(f1Mergef2.nrows)
    assert f2Mergef1.nrow == 1, "Expected one row  but actual number of row is {0}!".format(f2Mergef1.nrows)
    pyunit_utils.compare_frames_local(f1Mergef2, file1, prob=1)
    pyunit_utils.compare_frames_local(f2Mergef1, file1, prob=1)    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(mergeOneEmptyFrame)
else:
    mergeOneEmptyFrame()

