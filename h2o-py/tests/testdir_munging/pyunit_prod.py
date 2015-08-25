import sys
sys.path.insert(1, "../../")
import h2o, tests
import numpy as np
import random

def test_prod(ip,port):

    data = [[random.uniform(1,10)] for c in range(10)]
    h2o_data = h2o.H2OFrame(python_obj=data)
    np_data = np.array(data)

    h2o_prod = h2o_data.prod()
    np_prod = np.prod(np_data)

    assert abs(h2o_prod - np_prod) < 1e-06, "check unsuccessful! h2o computed {0} and numpy computed {1}. expected " \
                                            "equal quantile values between h2o and numpy".format(h2o_prod,np_prod)
    h2o.remove(h2o_data)
if __name__ == "__main__":
    tests.run_test(sys.argv, test_prod)
