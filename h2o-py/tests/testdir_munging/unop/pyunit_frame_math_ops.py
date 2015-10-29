import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils



import numpy as np
import random
import math
import scipy.special

def frame_math_ops():



    sin_cos_tan_atan_sinh_cosh_tanh_asinh_data = [[random.uniform(-10,10) for r in range(10)] for c in range(10)]
    asin_acos_atanh_data = [[random.uniform(-1,1) for r in range(10)] for c in range(10)]
    acosh_data = [[random.uniform(1,10) for r in range(10)] for c in range(10)]
    abs_data = [[random.uniform(-100000,0) for r in range(10)] for c in range(10)]
    signif_data = [[0.0000123456, 1], [2, 3]]

    h2o_data1 = h2o.H2OFrame(zip(*sin_cos_tan_atan_sinh_cosh_tanh_asinh_data))
    h2o_data2 = h2o.H2OFrame(asin_acos_atanh_data)
    h2o_data3 = h2o.H2OFrame(acosh_data)
    h2o_data4 = h2o.H2OFrame(abs_data)
    h2o_data5 = h2o.H2OFrame(signif_data)

    np_data1 = np.array(sin_cos_tan_atan_sinh_cosh_tanh_asinh_data)
    np_data2 = np.array(asin_acos_atanh_data)
    np_data3 = np.array(acosh_data)
    np_data4 = np.array(abs_data)

    for d in range(1,6):
        h2o_signif = h2o_data5.signif(digits=d)
        h2o_round = h2o_data5.round(digits=d+4)
        s = h2o_signif[0,0]
        r = h2o_round[0,0]
        assert s == r, "Expected these to be equal, but signif: {0}, round: {1}".format(s, r)
    h2o_transposed = h2o_data1[0:5].transpose()
    r, c = h2o_transposed.dim
    assert r == 5 and c == 10, "Expected 5 rows and 10 columns, but got {0} rows and {1} columns".format(r,c)
    pyunit_utils.np_comparison_check(h2o_transposed, np.transpose(np_data1[:,0:5]), 10)
    pyunit_utils.np_comparison_check(h2o_data1.cos(), np.cos(np_data1), 10)
    pyunit_utils.np_comparison_check(h2o_data1.sin(), np.sin(np_data1), 10)
    pyunit_utils.np_comparison_check(h2o_data1.tan(), np.tan(np_data1), 10)



if __name__ == "__main__":
    pyunit_utils.standalone_test(frame_math_ops)
else:
    frame_math_ops()
