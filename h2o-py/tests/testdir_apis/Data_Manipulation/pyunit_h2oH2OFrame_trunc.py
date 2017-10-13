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


def h2o_H2OFrame_trunc():
    """
    Python API test: h2o.frame.H2OFrame.trunc()
    """
    row_num = randrange(1,10)
    col_num = randrange(1,10)
    length_out_r = math.ceil(0.78*row_num)
    length_out_c = math.ceil(col_num*0.4)
    python_lists = np.random.randint(-5,5, (row_num, col_num))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)

    allframe = h2oframe.trunc()
    assert_is_type(allframe, H2OFrame)      # check return type
        # check values
    pyunit_utils.assert_correct_frame_operation(h2oframe, allframe, "floor")

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_trunc())
else:
    h2o_H2OFrame_trunc()
