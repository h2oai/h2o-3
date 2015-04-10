import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np
import random

def vec_math_ops(ip,port):
    # Connect to h2o
    h2o.init(ip,port)

    sin_cos_tan_atan_sinh_cosh_tanh_asinh_data = [[random.uniform(-10,10) for r in range(10)] for c in range(10)]
    asin_acos_atanh_data = [[random.uniform(-1,1) for r in range(10)] for c in range(10)]
    acosh_data = [[random.uniform(1,10) for r in range(10)] for c in range(10)]
    abs_data = [[random.uniform(-100000,0) for r in range(10)] for c in range(10)]

    h2o_data1 = h2o.H2OFrame(python_obj=sin_cos_tan_atan_sinh_cosh_tanh_asinh_data)
    h2o_data2 = h2o.H2OFrame(python_obj=asin_acos_atanh_data)
    h2o_data3 = h2o.H2OFrame(python_obj=acosh_data)
    h2o_data4 = h2o.H2OFrame(python_obj=abs_data)

    np_data1 = np.array(sin_cos_tan_atan_sinh_cosh_tanh_asinh_data)
    np_data2 = np.array(asin_acos_atanh_data)
    np_data3 = np.array(acosh_data)
    np_data4 = np.array(abs_data)

    row, col = h2o_data1.dim()

    def check_values(h2o_data, np_data):
        success = True
        for i in range(10):
            if not abs(h2o.as_list(h2o_data[i,0])[0][0] - np_data[i]) < 1e-06: success = False
        return success


    c = random.randint(0,col-1)
    assert check_values(h2o.cos(h2o_data1[c]), np.cos(np_data1[:,c])),       "expected equal cos values between h2o and numpy"
    assert check_values(h2o.sin(h2o_data1)[c], np.sin(np_data1[:,c])),       "expected equal sin values between h2o and numpy"
    assert check_values(h2o.tan(h2o_data1)[c], np.tan(np_data1[:,c])),       "expected equal tan values between h2o and numpy"
    assert check_values(h2o.acos(h2o_data2)[c], np.arccos(np_data2[:,c])),   "expected equal acos values between h2o and numpy"
    assert check_values(h2o.asin(h2o_data2)[c], np.arcsin(np_data2[:,c])),   "expected equal asin values between h2o and numpy"
    assert check_values(h2o.atan(h2o_data1)[c], np.arctan(np_data1[:,c])),   "expected equal atan values between h2o and numpy"
    assert check_values(h2o.cosh(h2o_data1)[c], np.cosh(np_data1[:,c])),     "expected equal cosh values between h2o and numpy"
    assert check_values(h2o.sinh(h2o_data1)[c], np.sinh(np_data1[:,c])),     "expected equal sinh values between h2o and numpy"
    assert check_values(h2o.tanh(h2o_data1)[c], np.tanh(np_data1[:,c])),     "expected equal tanh values between h2o and numpy"
    assert check_values(h2o.acosh(h2o_data3)[c], np.arccosh(np_data3[:,c])), "expected equal acosh values between h2o and numpy"
    assert check_values(h2o.asinh(h2o_data1)[c], np.arcsinh(np_data1[:,c])), "expected equal asinh values between h2o and numpy"
    assert check_values(h2o.atanh(h2o_data2)[c], np.arctanh(np_data2[:,c])), "expected equal atanh values between h2o and numpy"
    assert check_values(h2o.abs(h2o_data4)[c], np.fabs(np_data4[:,c])),      "expected equal abs values between h2o and numpy"
    assert check_values(h2o.sign(h2o_data2)[c], np.sign(np_data2[:,c])),     "expected equal sign values between h2o and numpy"
    assert check_values(h2o.sqrt(h2o_data3)[c], np.sqrt(np_data3[:,c])),     "expected equal sqrt values between h2o and numpy"
    assert check_values(h2o.trunc(h2o_data3)[c], np.trunc(np_data3[:,c])),   "expected equal trunc values between h2o and numpy"
    assert check_values(h2o.ceil(h2o_data3)[c], np.ceil(np_data3[:,c])),     "expected equal ceil values between h2o and numpy"
    assert check_values(h2o.floor(h2o_data3)[c], np.floor(np_data3[:,c])),   "expected equal floor values between h2o and numpy"
    assert check_values(h2o.log(h2o_data3)[c], np.log(np_data3[:,c])),       "expected equal log values between h2o and numpy"
    assert check_values(h2o.log10(h2o_data3)[c], np.log10(np_data3[:,c])),   "expected equal log10 values between h2o and numpy"
    assert check_values(h2o.log1p(h2o_data3)[c], np.log1p(np_data3[:,c])),   "expected equal log1p values between h2o and numpy"
    assert check_values(h2o.log2(h2o_data3)[c], np.log2(np_data3[:,c])),     "expected equal log2 values between h2o and numpy"

if __name__ == "__main__":
    h2o.run_test(sys.argv, vec_math_ops)