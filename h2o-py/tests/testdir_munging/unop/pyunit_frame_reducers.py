import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import numpy as np
import random

def frame_reducers():



    data = [[random.uniform(-10000,10000) for r in range(10)] for c in range(10)]
    h2o_data = h2o.H2OFrame(zip(*data))
    np_data = np.array(data)

    h2o_val = h2o_data.min()
    num_val = np.min(np_data)
    assert abs(h2o_val - num_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal min values between h2o and " \
        "numpy".format(h2o_val,num_val)
    h2o_val = h2o_data.max()
    num_val = np.max(np_data)
    assert abs(h2o_val - num_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal max values between h2o and " \
        "numpy".format(h2o_val,num_val)
    h2o_val = h2o_data.sum()
    num_val = np.sum(np_data)
    assert abs(h2o_val - num_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal sum values between h2o and " \
        "numpy".format(h2o_val,num_val)
    #pyunit_utils.np_comparison_check(h2o.var(h2o_data), np.cov(np_data, rowvar=0, ddof=1), 10)



if __name__ == "__main__":
    pyunit_utils.standalone_test(frame_reducers)
else:
    frame_reducers()
