from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# This test will make sure that an exception is thrown by the Java backend if we attempt to sort a
# frame containing String columns.
def sort():
    try:
        df = h2o.H2OFrame({"A":["another", "set", "of", "bad", "string"], "B":[10, 1, 2, 5, 7],
                           "C":["what", "is", "this", "thing", "doing"]})
        dfIntSorted = h2o.H2OFrame({"B":[1,2,5,7,10]})
        dfSortedIntCN = df.sort("B")
        pyunit_utils.compare_frames(dfIntSorted, dfSortedIntCN, df.nrow)
        assert False, "Sort could not work with String columns and an error should have been thrown but not..."
    except:
        assert True # expected error here as sort will not work with String columns in the frame

if __name__ == "__main__":
    pyunit_utils.standalone_test(sort)
else:
    sort()