import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



import random
import numpy as np

def mmult():
    data = [[random.uniform(-10000,10000)] for c in range(100)]
    h2o_data = h2o.H2OFrame(zip(*data))
    np_data = np.array(data)

    h2o_mm = h2o_data.mult(h2o_data.transpose())
    np_mm = np.dot(np_data, np.transpose(np_data))

    for x in range(10):
        for y in range(10):
            r = random.randint(0,99)
            c = random.randint(0,99)
            h2o_val = h2o_mm[r,c]
            np_val = np_mm[r][c]
            assert abs(h2o_val - np_val) < 1e-06, "check unsuccessful! h2o computed {0} and numpy computed {1}. expected " \
                                                  "equal quantile values between h2o and numpy".format(h2o_val,np_val)



if __name__ == "__main__":
    pyunit_utils.standalone_test(mmult)
else:
    mmult()
