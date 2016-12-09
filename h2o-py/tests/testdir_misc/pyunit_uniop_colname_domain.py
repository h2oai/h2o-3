from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

#Test to see if you apply H2O uniop methods to an H2OFrame it will not overwrite the original column headers & domains
# & also update colnames of new frame with `op(colName)` where `op` is a uniop operator.

def pyunit_colname_uniop():
    dataframe = {'A': [1,0,3,4], 'B': [5,6,-6, -1], 'C':[-4, -6, -7, 8]}
    frame = h2o.H2OFrame(dataframe)
    frame_asin = frame.asin()

    #Check colnames of original frame remain untouched.
    assert set(frame.names) == {"A", "B", "C"}, "Expected original colnames to remain the same after uniop operation"
    #Check new colnames for modified frame are of the convention `op(colname)`
    assert ["asin(%s)" % (name) for name in frame.names] == frame_asin.names,"Expected equal col names after uniop operation"

    #Check again after a refresh to the frame
    frame_asin.refresh()
    #Check new colnames for modified frame are of the convention `op(colname)`
    assert ["asin(%s)" % (name) for name in frame.names] == frame_asin.names,"Expected equal col names after uniop operation"
    #Check types are maintained
    assert frame_asin.types == {"asin(A)": "real", "asin(B)": "real", "asin(C)": "int"}, "Expect equal col types after" \
                                                                                            "uniop operation"
if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_colname_uniop)
else:
    pyunit_colname_uniop()