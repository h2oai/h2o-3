from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from random import randrange
import numpy as np
from h2o.frame import H2OFrame


def h2o_H2OFrame_logical_negation():
    """
    Python API test: h2o.frame.H2OFrame.logical_negation()
    """
    row_num = randrange(1,10)
    col_num = randrange(1,10)
    python_lists = np.zeros((row_num, col_num))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    clist = h2oframe.logical_negation()

    assert_is_type(clist, H2OFrame)     # check return type
    assert clist.all(), "h2o.H2OFrame.logical_negation() command is not working."  # check return result


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_logical_negation())
else:
    h2o_H2OFrame_logical_negation()
