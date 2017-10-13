from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
import numpy as np


def h2o_H2OFrame_nlevels():
    """
    Python API test: h2o.frame.H2OFrame.nlevels()
    """
    python_lists = np.random.randint(-2,2, (10000,2))
    h2oframe = h2o.H2OFrame(python_obj=python_lists, column_types=['enum', 'enum'])
    clist = h2oframe.nlevels()

    assert_is_type(clist, list)     # check return type
    assert len(clist)==2 and max(clist)==min(clist)==4, "h2o.H2OFrame.nlevels() command is not working."

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_nlevels())
else:
    h2o_H2OFrame_nlevels()
