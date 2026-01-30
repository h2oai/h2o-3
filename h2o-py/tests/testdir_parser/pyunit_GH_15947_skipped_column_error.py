import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# Seb has reported that skipped_columns does not work if skipped_columns is called with h2o.H2OFrame
def test_skipped_columns():
    data = [[1, 4, "a",  1], [2, 5, "b",  0], [3, 6, "", 1]]
    frame = h2o.H2OFrame(data, skipped_columns=[1, 2])
    assert frame.ncol == 2, "Expected column number: 2.  Actual: {0}".format(frame.ncol)
 
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_skipped_columns)
else:
    test_skipped_columns()
