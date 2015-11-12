import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import numpy as np
import random

def frame_reducers():



    data = [[random.uniform(-10000,10000) for r in range(10)] for c in range(10)]
    h2o_data = h2o.H2OFrame(data)
    np_data = np.array(data)

    row, col = h2o_data.dim

    c = random.randint(0,col-1)
    h2o_val = h2o_data[c].min()
    num_val = np.min(np_data[c])
    assert abs(h2o_val - num_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal min values between h2o and " \
        "numpy".format(h2o_val,num_val)
    h2o_val = h2o_data[c].max()
    num_val = np.max(np_data[c])
    assert abs(h2o_val - num_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal max values between h2o and " \
        "numpy".format(h2o_val,num_val)
    h2o_val = h2o_data[c].sum()
    num_val = np.sum(np_data[c])
    assert abs(h2o_val - num_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal sum values between h2o and " \
        "numpy".format(h2o_val,num_val)
    h2o_val = h2o_data[c].sd()[0]
    num_val = np.std(np_data[c], axis=0, ddof=1)
    assert abs(h2o_val - num_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal sd values between h2o and " \
        "numpy".format(h2o_val,num_val)
    h2o_val = h2o_data[c].var()
    num_val = np.var(np_data[c], ddof=1)
    assert abs(h2o_val - num_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal var values between h2o and " \
        "numpy".format(h2o_val,num_val)
    h2o_val = h2o_data[c].mean()[0]
    num_val = np.mean(np_data[c])
    assert abs(h2o_val - num_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal mean values between h2o and " \
        "numpy".format(h2o_val,num_val)
    h2o_val = h2o_data[c].median()
    num_val = np.median(np_data[c])
    assert abs(h2o_val - num_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal median values between h2o and " \
        "numpy".format(h2o_val,num_val)



if __name__ == "__main__":
    pyunit_utils.standalone_test(frame_reducers)
else:
    frame_reducers()
