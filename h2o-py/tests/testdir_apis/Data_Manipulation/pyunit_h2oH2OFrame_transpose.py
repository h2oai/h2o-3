from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.utils.typechecks import assert_is_type
from random import randrange
import numpy as np
from h2o.frame import H2OFrame


def h2o_H2OFrame_transpose():
    """
    Python API test: h2o.frame.H2OFrame.transpose()
    """
    row_num = randrange(1,10)
    col_num = randrange(1,10)
    python_lists = np.random.randint(-5,5, (row_num, col_num))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)
    newFrame = h2oframe.transpose()

    assert_is_type(newFrame, H2OFrame)     # check return type
    # check shape
    assert newFrame.shape==(h2oframe.ncol, h2oframe.nrow), "h2o.H2OFrame.transpose() command is not working."
    # check content
    pyunit_utils.compare_frames(h2oframe, newFrame.transpose(), h2oframe.nrow, tol_time=0, tol_numeric=1e-6)


if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_transpose())
else:
    h2o_H2OFrame_transpose()
