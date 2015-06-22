import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np
import random
import math
import scipy.special

def vec_math_ops(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    sin_cos_tan_atan_sinh_cosh_tanh_asinh_data = [[random.uniform(-10,10) for r in range(10)] for c in range(10)]
    asin_acos_atanh_data = [[random.uniform(-1,1) for r in range(10)] for c in range(10)]
    acosh_data = [[random.uniform(1,10) for r in range(10)] for c in range(10)]
    abs_data = [[random.uniform(-100000,0) for r in range(10)] for c in range(10)]
    zero_one_data = [random.randint(0,1) for c in range(10)]
    zero_one_data = [zero_one_data, zero_one_data]

    h2o_data1 = h2o.H2OFrame(python_obj=sin_cos_tan_atan_sinh_cosh_tanh_asinh_data)
    h2o_data2 = h2o.H2OFrame(python_obj=asin_acos_atanh_data)
    h2o_data3 = h2o.H2OFrame(python_obj=acosh_data)
    h2o_data4 = h2o.H2OFrame(python_obj=abs_data)
    h2o_data5 = h2o.H2OFrame(python_obj=zero_one_data)

    np_data1 = np.array(sin_cos_tan_atan_sinh_cosh_tanh_asinh_data)
    np_data2 = np.array(asin_acos_atanh_data)
    np_data3 = np.array(acosh_data)
    np_data4 = np.array(abs_data)
    np_data5 = np.array(zero_one_data)

    row, col = h2o_data1.dim()

    c = random.randint(0,col-1)
    for d in range(1,6):
        h2o_signif = h2o.signif(h2o_data5[c], digits=d)
        h2o_round = h2o.round(h2o_data5[c], digits=d+4)
        s = h2o_signif[0]
        r = h2o_round[0]
        assert s == r, "Expected these to be equal, but signif: {0}, round: {1}".format(s, r)
    h2o_transposed = h2o.transpose(h2o_data1[c])
    x, y = h2o_transposed.dim()
    assert x == 1 and y == 10, "Expected 1 row and 10 columns, but got {0} rows and {1} columns".format(x,y)
    h2o.np_comparison_check(h2o.cos(h2o_data1[c]), np.cos(np_data1[:,c]), 10)
    h2o.np_comparison_check(h2o.sin(h2o_data1[c]), np.sin(np_data1[:,c]), 10)
    h2o.np_comparison_check(h2o.tan(h2o_data1[c]), np.tan(np_data1[:,c]), 10)
    h2o.np_comparison_check(h2o.acos(h2o_data2[c]), np.arccos(np_data2[:,c]), 10)
    h2o.np_comparison_check(h2o.asin(h2o_data2[c]), np.arcsin(np_data2[:,c]), 10)
    h2o.np_comparison_check(h2o.atan(h2o_data1[c]), np.arctan(np_data1[:,c]), 10)
    h2o.np_comparison_check(h2o.cosh(h2o_data1[c]), np.cosh(np_data1[:,c]), 10)
    h2o.np_comparison_check(h2o.sinh(h2o_data1[c]), np.sinh(np_data1[:,c]), 10)
    h2o.np_comparison_check(h2o.tanh(h2o_data1[c]), np.tanh(np_data1[:,c]), 10)
    h2o.np_comparison_check(h2o.acosh(h2o_data3[c]), np.arccosh(np_data3[:,c]), 10)
    h2o.np_comparison_check(h2o.asinh(h2o_data1[c]), np.arcsinh(np_data1[:,c]), 10)
    h2o.np_comparison_check(h2o.atanh(h2o_data2[c]), np.arctanh(np_data2[:,c]), 10)
    h2o.np_comparison_check(h2o.abs(h2o_data4[c]), np.fabs(np_data4[:,c]), 10)
    h2o.np_comparison_check(h2o.sign(h2o_data2[c]), np.sign(np_data2[:,c]), 10)
    h2o.np_comparison_check(h2o.sqrt(h2o_data3[c]), np.sqrt(np_data3[:,c]), 10)
    h2o.np_comparison_check(h2o.trunc(h2o_data3[c]), np.trunc(np_data3[:,c]), 10)
    h2o.np_comparison_check(h2o.ceil(h2o_data3[c]), np.ceil(np_data3[:,c]), 10)
    h2o.np_comparison_check(h2o.floor(h2o_data3[c]), np.floor(np_data3[:,c]), 10)
    h2o.np_comparison_check(h2o.log(h2o_data3[c]), np.log(np_data3[:,c]), 10)
    h2o.np_comparison_check(h2o.log10(h2o_data3[c]), np.log10(np_data3[:,c]), 10)
    h2o.np_comparison_check(h2o.log1p(h2o_data3[c]), np.log1p(np_data3[:,c]), 10)
    h2o.np_comparison_check(h2o.log2(h2o_data3[c]), np.log2(np_data3[:,c]), 10)
    h2o.np_comparison_check(h2o.exp(h2o_data3[c]), np.exp(np_data3[:,c]), 10)
    h2o.np_comparison_check(h2o.expm1(h2o_data3[c]), np.expm1(np_data3[:,c]), 10)
    h2o_val = h2o.gamma(h2o_data3[c])[5]
    num_val = math.gamma(h2o_data3[5,c])
    assert abs(h2o_val - num_val) <  max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal gamma values between h2o and" \
        "math".format(h2o_val,num_val)
    h2o_val = h2o.lgamma(h2o_data3[c])[5]
    num_val = math.lgamma(h2o_data3[5,c])
    assert abs(h2o_val - num_val) <  max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal lgamma values between h2o and " \
        "math".format(h2o_val,num_val)
    h2o_val = h2o.digamma(h2o_data3[c])[5]
    num_val = scipy.special.polygamma(0,h2o_data3[5,c])
    assert abs(h2o_val - num_val) <  max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal digamma values between h2o and " \
        "math".format(h2o_val,num_val)
    h2o_val = h2o.trigamma(h2o_data3[c])[5]
    num_val = scipy.special.polygamma(1,h2o_data3[5,c])
    assert abs(h2o_val - num_val) <  max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal trigamma values between h2o and " \
        "math".format(h2o_val,num_val)
    for c in range(col):
        h2o_val = h2o.all(h2o_data5[c])
        num_val = True if np.all(np_data5[:,c]) else False
        assert h2o_val == num_val, "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal " \
                                   "values between h2o and numpy".format(h2o_val,num_val)

if __name__ == "__main__":
    h2o.run_test(sys.argv, vec_math_ops)
