from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame

def h2o_H2OFrame_pop():
    """
    Python API test: h2o.frame.H2OFrame.pop(i)

    Copied from pyunit_pop.py
    """
    pros = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    nc = pros.ncol

    popped_col = pros.pop(pros.names[0])    # pop with string column name
    assert_is_type(popped_col, H2OFrame)
    assert popped_col.ncol==1, "h2o.H2OFrame.pop() command is not working."
    assert pros.ncol==nc-1, "h2o.H2OFrame.pop()command is not working."

    popped_col = pros.pop(0)    # pop with integer index
    assert_is_type(popped_col, H2OFrame)
    assert popped_col.ncol==1, "h2o.H2OFrame.pop() command is not working."
    assert pros.ncol==nc-2, "h2o.H2OFrame.pop()command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_pop())
else:
    h2o_H2OFrame_pop()
