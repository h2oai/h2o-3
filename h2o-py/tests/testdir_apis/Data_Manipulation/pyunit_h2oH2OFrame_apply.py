from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_apply():
    """
    Python API test: h2o.frame.H2OFrame.apply(fun=None, axis=0)
    """
    python_lists = [[1,2,3,4], [1,2,3,4]]
    h2oframe = h2o.H2OFrame(python_obj=python_lists, na_strings=['NA'])
    colMean = h2oframe.apply(lambda x: x.mean(), axis=0)
    rowMean = h2oframe.apply(lambda x: x.mean(), axis=1)
    assert_is_type(colMean, H2OFrame)
    assert_is_type(rowMean, H2OFrame)
    assert rowMean[0,0]==rowMean[1,0] and (rowMean[0,0]-2.5)<1e-10, "h2o.H2OFrame.apply() command is not working."
    assert (colMean[0,0]==h2oframe[0,0]) and (colMean[0,1]==h2oframe[0,1]) and (colMean[0,2]==h2oframe[0,2]) and \
           (colMean[0,3]==h2oframe[0,3]), "h2o.H2OFrame.apply() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_apply())
else:
    h2o_H2OFrame_apply()
