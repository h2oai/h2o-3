from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from random import randrange
import numpy as np


def h2o_H2OFrame_isstring():
    """
    Python API test: h2o.frame.H2OFrame.isstring(), h2o.frame.H2OFrame.ischaracter() [DEPRECATED] Use frame.isstring()
    """
    row_num = randrange(1,10)
    col_num = randrange(1,10)
    python_lists = np.random.randint(-5,5, (row_num, col_num))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    newFrame = h2oframe.asfactor().ascharacter()
    clist = newFrame.isstring()
    assert_is_type(clist, list)     # check return type
    assert sum(clist)==col_num, "h2o.H2OFrame.isstring() command is not working."  # check return result

    clist2 = newFrame.ischaracter()
    assert_is_type(clist2, list)     # check return type
    assert sum(clist2)==col_num, "h2o.H2OFrame.ischaracter() command is not working."  # check return result


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_isstring())
else:
    h2o_H2OFrame_isstring()
