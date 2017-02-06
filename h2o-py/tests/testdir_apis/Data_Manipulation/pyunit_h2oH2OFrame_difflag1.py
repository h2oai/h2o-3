from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame


def h2o_H2OFrame_difflag1():
    """
    Python API test: h2o.frame.H2OFrame.difflag1()
    """
    python_object=[list(range(10)), list(range(10))]
    foo = h2o.H2OFrame(python_obj=np.transpose(python_object))

    diffs = foo[0].difflag1()   # default
    results = diffs==1.0
    # check correct return type
    assert_is_type(diffs, H2OFrame)
    assert results.sum().flatten()==foo.nrow-1, "h2o.H2OFrame.difflag1() command is not working."
    # To-do: check correct result


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_difflag1())
else:
    h2o_H2OFrame_difflag1()
