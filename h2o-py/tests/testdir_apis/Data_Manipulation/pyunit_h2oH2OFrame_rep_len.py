from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame
from random import randrange
import numpy as np
import math


def h2o_H2OFrame_rep_len():
    """
    Python API test: h2o.frame.H2OFrame.rep_len(length_out)
    """
    row_num = randrange(1,10)
    col_num = randrange(1,10)
    length_out_r = math.ceil(0.78*row_num)
    python_lists = np.random.randint(-5,5, (row_num, col_num))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)

    one_column = h2oframe[0].rep_len(length_out=(length_out_r+row_num))       # one column, duplicate row
    assert_is_type(one_column, H2OFrame)    # check return type
        # check shape
    assert one_column.shape == (length_out_r+row_num, 1), "h2o.H2OFrame.rep_len() command is not working."

    # check values
    repeat_row_start = row_num
    repeat_row_end = row_num+length_out_r
    pyunit_utils.compare_frames(h2oframe[0:length_out_r,0], one_column[repeat_row_start:repeat_row_end, 0],
                                length_out_r, tol_time=0, tol_numeric=1e-6, strict=False, compare_NA=True)


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_rep_len())
else:
    h2o_H2OFrame_rep_len()
