import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



import random
import numpy as np

def quantile():
    # Connect to a pre-existing cluster
    

    data = [[random.uniform(-10000,10000)] for c in range(1000)]
    h2o_data = h2o.H2OFrame(zip(*data))
    np_data = np.array(data)

    h2o_quants = h2o_data.quantile()
    np_quants = np.percentile(np_data,[1, 10, 25, 33.3, 50, 66.7, 75, 90, 99],axis=0)

    for e in range(9):
        h2o_val = h2o_quants[e,1]
        np_val = np_quants[e][0]
        assert abs(h2o_val - np_val) < 1e-06, \
        "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal quantile values between h2o " \
        "and numpy".format(h2o_val,np_val)



if __name__ == "__main__":
    pyunit_utils.standalone_test(quantile)
else:
    quantile()
