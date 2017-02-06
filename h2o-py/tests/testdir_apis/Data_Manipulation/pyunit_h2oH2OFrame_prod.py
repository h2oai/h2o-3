from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
from tests import pyunit_utils
import h2o
import numpy as np
import random


def h2o_H2OFrame_prod():
    """
    Python API test: h2o.frame.H2OFrame.prod(na_rm=False)

    Copied from pyunit_prod.py
    """
    data = [[random.uniform(1,10)] for c in range(10)]
    h2o_data = h2o.H2OFrame(data)
    np_data = np.array(data)

    h2o_prod = h2o_data.prod(na_rm=True)
    np_prod = np.prod(np_data)

    assert abs(h2o_prod - np_prod) < 1e-06, "check unsuccessful! h2o computed {0} and numpy computed {1}. expected " \
                                            "equal quantile values between h2o and numpy".format(h2o_prod,np_prod)

    h2o.remove(h2o_data)
if __name__ == "__main__":
    pyunit_utils.standalone_test(h2o_H2OFrame_prod())
else:
    h2o_H2OFrame_prod()
