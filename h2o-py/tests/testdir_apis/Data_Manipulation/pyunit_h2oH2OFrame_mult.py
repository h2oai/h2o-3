from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame
import random

def h2o_H2OFrame_mult():
    """
    Python API test: h2o.frame.H2OFrame.mult(matrix)

    Copied from pyunit_mmult.py
    """
    data = [[random.uniform(-10000,10000)] for c in range(100)]
    h2o_data = h2o.H2OFrame(data)
    np_data = np.array(data)

    h2o_mm = h2o_data.mult(h2o_data.transpose())
    np_mm = np.dot(np_data, np.transpose(np_data))

    assert_is_type(h2o_mm, H2OFrame)

    for x in range(10):
        for y in range(10):
            r = random.randint(0,99)
            c = random.randint(0,99)
            h2o_val = h2o_mm[r,c]
            np_val = np_mm[r][c]
            assert abs(h2o_val - np_val) < 1e-06, "check unsuccessful! h2o computed {0} and numpy computed {1}. expected " \
                                                  "equal quantile values between h2o and numpy".format(h2o_val,np_val)

if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_mult())
else:
    h2o_H2OFrame_mult()
