import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



import numpy as np
import random

def test_prod():

    data = [[random.uniform(1,10)] for c in range(10)]
    h2o_data = h2o.H2OFrame(zip(*data))
    np_data = np.array(data)

    h2o_prod = h2o_data.prod()
    np_prod = np.prod(np_data)

    assert abs(h2o_prod - np_prod) < 1e-06, "check unsuccessful! h2o computed {0} and numpy computed {1}. expected " \
                                            "equal quantile values between h2o and numpy".format(h2o_prod,np_prod)
    h2o.remove(h2o_data)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_prod)
else:
    test_prod()
