from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame
import operator


def h2o_H2OFrame_cummax():
    """
    Python API test: h2o.frame.H2OFrame.cummax(axis=0), h2o.frame.H2OFrame.cummin(axis=0),
    h2o.frame.H2OFrame.cumprod(axis=0), h2o.frame.H2OFrame.cumsum(axis=0)

    Copied from pyunit_cumsum_cumprod_cummin_cummax.py
    """
    python_object=[list(range(1,10)), list(range(9,0,-1))]
    python_object_transpose = np.transpose(python_object)
    foo = h2o.H2OFrame(python_obj=python_object_transpose)

    cummax_col = foo.cummax(axis=0)
    cummin_col = foo.cummin(axis=0)
    cumprod_col = foo.cumprod(axis=0)
    cumsum_col = foo.cumsum(axis=0)

    # check return types
    assert_is_type(cummax_col, H2OFrame)
    assert_is_type(cummin_col, H2OFrame)
    assert_is_type(cumprod_col, H2OFrame)
    assert_is_type(cumsum_col, H2OFrame)

    # compare results of cum operations
    checkOpsCorrect(foo, cummax_col, max)   # cummax
    checkOpsCorrect(foo, cummin_col, min)   # cummin
    checkOpsCorrect(foo, cumprod_col, operator.mul) # cumprod
    checkOpsCorrect(foo, cumsum_col, operator.add)  # cumsum

def checkOpsCorrect(sourceFrame, resultFrame, op):
    f0 = pyunit_utils.cumop(sourceFrame, op, 0)
    tempFrame = h2o.H2OFrame(python_obj=np.transpose(f0))
    f1 = pyunit_utils.cumop(sourceFrame, op, 1)
    tempFrame=tempFrame.cbind(h2o.H2OFrame(python_obj=np.transpose(f1)))
    pyunit_utils.compare_frames(resultFrame, tempFrame, numElements=9)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_cummax())
else:
    h2o_H2OFrame_cummax()
