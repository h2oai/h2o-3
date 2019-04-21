from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from random import randrange


def h2o_H2OFrame_show():
    """
    Python API test: h2o.frame.H2OFrame.show(use_pandas=False)
    """
    row_num = randrange(1,10)
    col_num = randrange(1,10)
    python_lists = np.random.randint(-5,5, (row_num, col_num))
    h2oframe = h2o.H2OFrame(python_obj=python_lists)

    h2oframe.show(use_pandas=True)
    h2oframe.show(use_pandas=False)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_show())
else:
    h2o_H2OFrame_show()
