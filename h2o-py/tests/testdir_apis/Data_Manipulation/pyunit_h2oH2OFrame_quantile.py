from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
from h2o.utils.typechecks import assert_is_type
from h2o.frame import H2OFrame
import random


def h2o_H2OFrame_quantile():
    """
    Python API test: h2o.frame.H2OFrame.quantile(prob=None, combine_method='interpolate', weights_column=None)

    Copied from pyunit_quantile.py
    """
    data = [[random.uniform(-10000,10000)] for c in range(1000)]
    h2o_data = h2o.H2OFrame(data)
    np_data = np.array(data)
    h2o_quants = h2o_data.quantile(prob=None, combine_method='interpolate', weights_column=None)
    assert_is_type(h2o_quants, H2OFrame)

    np_quants = np.percentile(np_data,[1, 10, 25, 33.3, 50, 66.7, 75, 90, 99],axis=0)

    for e in range(9):
        h2o_val = h2o_quants[e,1]
        np_val = np_quants[e][0]
        assert abs(h2o_val - np_val) < 1e-06, \
            "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal quantile values between h2o " \
            "and numpy".format(h2o_val,np_val)

    h2o.remove(h2o_data)
if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_quantile())
else:
    h2o_H2OFrame_quantile()
