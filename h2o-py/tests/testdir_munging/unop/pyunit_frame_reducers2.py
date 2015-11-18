import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import numpy as np
import random

def expr_reducers():



    data = [[random.uniform(-10000,10000) for r in range(10)] for c in range(10)]
    h2o_data_1 = h2o.H2OFrame(zip(*data))
    np_data = np.array(data)
    row, col = h2o_data_1.dim
    h2o_data = h2o_data_1 + 2
    np_data = np_data + 2

    def check_values(h2o_data, numpy_data):
        success = True
        for i in range(10):
            r = random.randint(0,row-1)
            c = random.randint(0,col-1)
            h2o_val = h2o_data[r,c]
            num_val = numpy_data[r,c]
            if not abs(h2o_val - num_val) < 1e-06:
                success = False
                print "check unsuccessful! h2o computed {0} and numpy computed {1}".format(h2o_val,num_val)
        return success

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
    pyunit_utils.np_comparison_check(h2o_data.var(), np.cov(np_data, rowvar=0, ddof=1), 10), \
        "expected equal var values between h2o and numpy"



if __name__ == "__main__":
    pyunit_utils.standalone_test(expr_reducers)
else:
    expr_reducers()
