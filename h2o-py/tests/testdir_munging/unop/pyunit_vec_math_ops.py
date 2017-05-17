from builtins import zip
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import numpy as np
import random
import math
import scipy.special


def vec_math_ops():
    seed0 = random.random()
    random.seed(seed0)
    print("Using seed %r" % seed0)

    sin_cos_tan_atan_sinh_cosh_tanh_asinh_data = [[random.uniform(-10,10) for r in range(10)] for c in range(10)]
    asin_acos_atanh_data = [[random.uniform(-1,1) for r in range(10)] for c in range(10)]
    acosh_data = [[random.uniform(1,10) for r in range(10)] for c in range(10)]
    abs_data = [[random.uniform(-100000,0) for r in range(10)] for c in range(10)]
    zero_one_data = [random.randint(0,1) for c in range(10)]
    zero_one_data = [zero_one_data, zero_one_data]

    h2o_data1 = h2o.H2OFrame(sin_cos_tan_atan_sinh_cosh_tanh_asinh_data)
    h2o_data2 = h2o.H2OFrame(asin_acos_atanh_data)
    h2o_data3 = h2o.H2OFrame(acosh_data)
    h2o_data4 = h2o.H2OFrame(abs_data)
    h2o_data5 = h2o.H2OFrame(zero_one_data)

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

    test1 = (h2o_data1 == h2o_data1).all()
    test2 = (h2o_data1 == h2o_data2).all()
    assert test1 is True and test2 is False, "API change detected, the tests below will be ineffective"

    print("Testing trigonometric functions")
    assert ((h2o_data1.cos() - h2o.H2OFrame(np.cos(np_data1))).abs() < 1e-12).all()
    assert ((h2o_data1.sin() - h2o.H2OFrame(np.sin(np_data1))).abs() < 1e-12).all()
    assert ((h2o_data1.tan() - h2o.H2OFrame(np.tan(np_data1))).abs() < 1e-9 * (h2o_data1.tan().abs() + 1)).all()
    print("Testing inverse trigonometric functions")
    assert ((h2o_data2.acos() - h2o.H2OFrame(np.arccos(np_data2))).abs() < 1e-12).all()
    assert ((h2o_data2.asin() - h2o.H2OFrame(np.arcsin(np_data2))).abs() < 1e-12).all()
    assert ((h2o_data1.atan() - h2o.H2OFrame(np.arctan(np_data1))).abs() < 1e-12 * (h2o_data1.tan().abs() + 1)).all()
    print("Testing hyperbolic trigonometric functions")
    assert ((h2o_data1.cosh() - h2o.H2OFrame(np.cosh(np_data1))).abs() < 1e-12 * h2o_data1.cosh().abs()).all()
    assert ((h2o_data1.sinh() - h2o.H2OFrame(np.sinh(np_data1))).abs() < 1e-12 * h2o_data1.sinh().abs()).all()
    assert ((h2o_data1.tanh() - h2o.H2OFrame(np.tanh(np_data1))).abs() < 1e-12 * h2o_data1.tanh().abs()).all()
    assert ((h2o_data3.acosh() - h2o.H2OFrame(np.arccosh(np_data3))).abs() < 1e-9 * h2o_data3.acosh().abs()).all()
    assert ((h2o_data1.asinh() - h2o.H2OFrame(np.arcsinh(np_data1))).abs() < 1e-12 * h2o_data1.asinh().abs()).all()
    assert ((h2o_data2.atanh() - h2o.H2OFrame(np.arctanh(np_data2))).abs() < 1e-9 * h2o_data2.atanh().abs()).all()

    print("Testing gamma functions")
    x_val = h2o_data3[5, c]
    assert type(x_val) is float
    h2o_val = h2o_data3[c].gamma()[5, :].flatten()
    num_val = math.gamma(x_val)
    assert abs(h2o_val - num_val) < max(abs(h2o_val), abs(num_val)) * 1e-5, \
        "h2o computed gamma({0}) = {1} while math computed gamma({0}) = {2}".format(x_val, h2o_val, num_val)

    h2o_val = h2o_data3[c].lgamma()[5, :].flatten()
    num_val = math.lgamma(x_val)
    assert abs(h2o_val - num_val) < max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "h2o computed lgamma({0}) = {1} while math computed lgamma({0}) = {2}".format(x_val, h2o_val, num_val)

    h2o_val = h2o_data3[c].digamma()[5, :].flatten()
    num_val = scipy.special.polygamma(0, x_val)
    assert abs(h2o_val - num_val) < max(abs(h2o_val), abs(num_val)) * 1e-5, \
        "h2o computed digamma({0}) = {1} while scipy computed digamma({0}) = {2}".format(x_val, h2o_val, num_val)

    h2o_val = h2o_data3[c].trigamma()[5, :].flatten()
    num_val = scipy.special.polygamma(1, x_val)
    assert abs(h2o_val - num_val) < max(abs(h2o_val), abs(num_val)) * 1e-6, \
        "h2o computed trigamma({0}) = {1} while scipy computed trigamma({0}) = {2}".format(x_val, h2o_val, num_val)

    # for c in range(col):
    #     h2o_val = h2o_data5[c].all()
    #     num_val = True if np.all(np_data5[:,c]) else False
    #     assert h2o_val == num_val, "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal " \
    #                                "values between h2o and numpy".format(h2o_val,num_val)



if __name__ == "__main__":
    pyunit_utils.standalone_test(vec_math_ops)
else:
    vec_math_ops()
