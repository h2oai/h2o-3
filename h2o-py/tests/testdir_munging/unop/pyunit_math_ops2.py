import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np
import random
import math
import scipy.special


def expr_math_ops():
    sin_cos_tan_atan_sinh_cosh_tanh_asinh_data = [[random.uniform(-10,10) for r in range(10)] for c in range(10)]
    asin_acos_atanh_data = [[random.uniform(-1,1) for r in range(10)] for c in range(10)]
    acosh_data = [[random.uniform(1,10) for r in range(10)] for c in range(10)]
    abs_data = [[random.uniform(-100000,0) for r in range(10)] for c in range(10)]

    h2o_data1_1 = h2o.H2OFrame(zip(*sin_cos_tan_atan_sinh_cosh_tanh_asinh_data))
    h2o_data2_1 = h2o.H2OFrame(zip(*asin_acos_atanh_data))
    h2o_data3_1 = h2o.H2OFrame(zip(*acosh_data))
    h2o_data4_1 = h2o.H2OFrame(zip(*abs_data))

    np_data1 = np.array(sin_cos_tan_atan_sinh_cosh_tanh_asinh_data)
    np_data2 = np.array(asin_acos_atanh_data)
    np_data3 = np.array(acosh_data)
    np_data4 = np.array(abs_data)

    h2o_data1 = h2o_data1_1 + 2
    h2o_data2 = h2o_data2_1 / 1.01
    h2o_data3 = h2o_data3_1 * 1.5
    h2o_data4 = h2o_data4_1 - 1.5

    np_data1 = np_data1 + 2
    np_data2 = np_data2 / 1.01
    np_data3 = np_data3 * 1.5
    np_data4 = np_data4 - 1.5

    pyunit_utils.np_comparison_check(h2o_data1.cos(), np.cos(np_data1), 10)
    pyunit_utils.np_comparison_check(h2o_data1.sin(), np.sin(np_data1), 10)
    pyunit_utils.np_comparison_check(h2o_data1.tan(), np.tan(np_data1), 10)
    pyunit_utils.np_comparison_check(h2o_data2.acos(), np.arccos(np_data2), 10)
    pyunit_utils.np_comparison_check(h2o_data2.asin(), np.arcsin(np_data2), 10)
    pyunit_utils.np_comparison_check(h2o_data1.atan(), np.arctan(np_data1), 10)
    pyunit_utils.np_comparison_check(h2o_data1.cosh(), np.cosh(np_data1), 10)
    pyunit_utils.np_comparison_check(h2o_data1.sinh(), np.sinh(np_data1), 10)
    pyunit_utils.np_comparison_check(h2o_data1.tanh(), np.tanh(np_data1), 10)
    pyunit_utils.np_comparison_check(h2o_data3.acosh(), np.arccosh(np_data3), 10)
    pyunit_utils.np_comparison_check(h2o_data1.asinh(), np.arcsinh(np_data1), 10)
    pyunit_utils.np_comparison_check(h2o_data2.atanh(), np.arctanh(np_data2), 10)
    pyunit_utils.np_comparison_check((h2o_data2/math.pi).cospi(), np.cos(np_data2), 10)
    pyunit_utils.np_comparison_check((h2o_data2/math.pi).sinpi(), np.sin(np_data2), 10)
    pyunit_utils.np_comparison_check((h2o_data2/math.pi).tanpi(), np.tan(np_data2), 10)
    pyunit_utils.np_comparison_check(h2o_data4.abs(), np.fabs(np_data4), 10)
    pyunit_utils.np_comparison_check(h2o_data2.sign(), np.sign(np_data2), 10)
    pyunit_utils.np_comparison_check(h2o_data3.sqrt(), np.sqrt(np_data3), 10)
    pyunit_utils.np_comparison_check(h2o_data3.trunc(), np.trunc(np_data3), 10)
    pyunit_utils.np_comparison_check(h2o_data3.ceil(), np.ceil(np_data3), 10)
    pyunit_utils.np_comparison_check(h2o_data3.floor(), np.floor(np_data3), 10)
    pyunit_utils.np_comparison_check(h2o_data3.log(), np.log(np_data3), 10)
    pyunit_utils.np_comparison_check(h2o_data3.log10(), np.log10(np_data3), 10)
    pyunit_utils.np_comparison_check(h2o_data3.log1p(), np.log1p(np_data3), 10)
    pyunit_utils.np_comparison_check(h2o_data3.log2(), np.log2(np_data3), 10)
    pyunit_utils.np_comparison_check(h2o_data3.exp(), np.exp(np_data3), 10)
    pyunit_utils.np_comparison_check(h2o_data3.expm1(), np.expm1(np_data3), 10)
    h2o_val = h2o_data3.gamma()[5,5]
    num_val = math.gamma(h2o_data3[5,5])
    assert abs(h2o_val - num_val) < max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal gamma values between h2o and " \
        "math".format(h2o_val,num_val)
    h2o_val = h2o_data3.lgamma()[5,5]
    num_val = math.lgamma(h2o_data3[5,5])
    assert abs(h2o_val - num_val) < max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal lgamma values between h2o and " \
        "math".\
            format(h2o_val,num_val)
    h2o_val = h2o_data3.digamma()[5,5]
    num_val = scipy.special.polygamma(0,h2o_data3[5,5])
    assert abs(h2o_val - num_val) < max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal digamma values between h2o and " \
        "math"\
            .format(h2o_val,num_val)
    h2o_val = h2o_data3.trigamma()[5,5]
    num_val = float(scipy.special.polygamma(1,h2o_data3[5,5]))
    assert abs(h2o_val - num_val) < max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "check unsuccessful! h2o computed {0} and math computed {1}. expected equal trigamma values between h2o and " \
        "math".format(h2o_val,num_val)



if __name__ == "__main__":
    pyunit_utils.standalone_test(expr_math_ops)
else:
    expr_math_ops()
