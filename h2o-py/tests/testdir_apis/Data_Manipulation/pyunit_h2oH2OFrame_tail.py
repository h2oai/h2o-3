from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from random import randrange
import numpy as np
from h2o.frame import H2OFrame


def h2o_H2OFrame_tail():
    """
    Python API test: h2o.frame.H2OFrame.tail(rows=10, cols=200)
    """
    row_num = randrange(2,10)
    col_num = randrange(2,10)
    python_lists = np.random.randint(-5,5, (row_num, col_num))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    new_row = randrange(1, row_num)
    new_col = randrange(1, col_num)
    newFrame = h2oframe.tail(rows=new_row, cols=new_col)

    assert_is_type(newFrame, H2OFrame)     # check return type
    assert newFrame.shape==(new_row, new_col), "h2o.H2OFrame.tail() command is not working."  # check return result

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_tail())
else:
    h2o_H2OFrame_tail()
