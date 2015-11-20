import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np
import random
import math
import scipy.special


def vec_math_ops():

    sin_cos_tan_atan_sinh_cosh_tanh_asinh_data = [[random.uniform(-10,10) for r in range(10)] for c in range(10)]
    asin_acos_atanh_data = [[random.uniform(-1,1) for r in range(10)] for c in range(10)]
    acosh_data = [[random.uniform(1,10) for r in range(10)] for c in range(10)]
    abs_data = [[random.uniform(-100000,0) for r in range(10)] for c in range(10)]
    zero_one_data = [random.randint(0,1) for c in range(10)]
    zero_one_data = [zero_one_data, zero_one_data]

    h2o_data1 = h2o.H2OFrame(zip(*sin_cos_tan_atan_sinh_cosh_tanh_asinh_data))
    h2o_data2 = h2o.H2OFrame(zip(*asin_acos_atanh_data))
    h2o_data3 = h2o.H2OFrame(zip(*acosh_data))
    h2o_data4 = h2o.H2OFrame(zip(*abs_data))
    h2o_data5 = h2o.H2OFrame(zip(*zero_one_data))

    np_data1 = np.array(sin_cos_tan_atan_sinh_cosh_tanh_asinh_data)
    np_data2 = np.array(asin_acos_atanh_data)
    np_data3 = np.array(acosh_data)
    np_data4 = np.array(abs_data)
    np_data5 = np.array(zero_one_data)

    row, col = h2o_data1.dim

    c = random.randint(0,col-1)
    for d in range(1,6):
        h2o_signif = h2o_data5[c].signif(digits=d)
        h2o_round = h2o_data5[c].round(digits=d+4)
        s = h2o_signif[0]
        r = h2o_round[0]
        assert (s == r).all(), "Expected these to be equal, but signif: {0}, round: {1}".format(s, r)
    h2o_transposed = h2o_data1[c].transpose()
    x, y = h2o_transposed.dim
    assert x == 1 and y == 10, "Expected 1 row and 10 columns, but got {0} rows and {1} columns".format(x,y)
    pyunit_utils.np_comparison_check(h2o_data1[:,c].cos(), np.cos(np_data1[:,c]), 10)
    pyunit_utils.np_comparison_check(h2o_data1[:,c].sin(), np.sin(np_data1[:,c]), 10)
    pyunit_utils.np_comparison_check(h2o_data1[:,c].tan(), np.tan(np_data1[:,c]), 10)
    pyunit_utils.np_comparison_check(h2o_data2[:,c].acos(), np.arccos(np_data2[:,c]), 10)
    pyunit_utils.np_comparison_check(h2o_data2[:,c].asin(), np.arcsin(np_data2[:,c]), 10)
    pyunit_utils.np_comparison_check(h2o_data1[:,c].atan(), np.arctan(np_data1[:,c]), 10)
    pyunit_utils.np_comparison_check(h2o_data1[:,c].cosh(), np.cosh(np_data1[:,c]), 10)
    pyunit_utils.np_comparison_check(h2o_data1[c].sinh(), np.sinh(np_data1[:,c]), 10)
    pyunit_utils.np_comparison_check(h2o_data1[c].tanh(), np.tanh(np_data1[:,c]), 10)
    pyunit_utils.np_comparison_check(h2o_data3[c].acosh(), np.arccosh(np_data3[:,c]), 10)
    pyunit_utils.np_comparison_check(h2o_data1[c].asinh(), np.arcsinh(np_data1[:,c]), 10)
    h2o_val = h2o_data3[c].gamma()[5,:].flatten()
    num_val = math.gamma(h2o_data3[5,c])
    assert abs(h2o_val - num_val) <  max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal gamma values between h2o and" \
        "math".format(h2o_val,num_val)
    h2o_val = h2o_data3[c].lgamma()[5,:].flatten()
    num_val = math.lgamma(h2o_data3[5,c])
    assert abs(h2o_val - num_val) <  max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal lgamma values between h2o and " \
        "math".format(h2o_val,num_val)
    h2o_val = h2o_data3[c].digamma()[5,:].flatten()
    num_val = scipy.special.polygamma(0,h2o_data3[5,c])
    assert abs(h2o_val - num_val) <  max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal digamma values between h2o and " \
        "math".format(h2o_val,num_val)
    h2o_val = h2o_data3[c].trigamma()[5,:].flatten()
    num_val = scipy.special.polygamma(1,h2o_data3[5,c])
    assert abs(h2o_val - float(num_val)) <  max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal trigamma values between h2o and " \
        "math".format(h2o_val,num_val)
    # for c in range(col):
    #     h2o_val = h2o_data5[c].all()
    #     num_val = True if np.all(np_data5[:,c]) else False
    #     assert h2o_val == num_val, "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal " \
    #                                "values between h2o and numpy".format(h2o_val,num_val)



if __name__ == "__main__":
    pyunit_utils.standalone_test(vec_math_ops)
else:
    vec_math_ops()
