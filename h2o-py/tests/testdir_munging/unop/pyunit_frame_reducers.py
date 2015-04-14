import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np
import random

def frame_reducers(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    data = [[random.uniform(-10000,10000) for r in range(10)] for c in range(10)]
    h2o_data = h2o.H2OFrame(python_obj=data)
    np_data = np.array(data)

    row, col = h2o_data.dim()

    def check_values(h2o_data, numpy_data):
        success = True
        for i in range(10):
            r = random.randint(0,row-1)
            c = random.randint(0,col-1)
            if not abs(h2o.as_list(h2o_data[r,c])[0][0] - numpy_data[r,c]) < 1e-06: success = False
        return success

    assert h2o.as_list(h2o.min(h2o_data))[0][0] - np.min(np_data) < 1e-06, \
        "expected equal min values between h2o and numpy"
    assert h2o.as_list(h2o.max(h2o_data))[0][0] - np.max(np_data) < 1e-06, \
        "expected equal max values between h2o and numpy"
    assert h2o.as_list(h2o.sum(h2o_data))[0][0] - np.sum(np_data) < 1e-06, \
        "expected equal sum values between h2o and numpy"
    assert check_values(h2o.var(h2o_data), np.cov(np_data, rowvar=0, ddof=1)), \
        "expected equal var values between h2o and numpy"

if __name__ == "__main__":
    h2o.run_test(sys.argv, frame_reducers)