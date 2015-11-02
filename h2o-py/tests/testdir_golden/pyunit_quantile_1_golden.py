import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils



import random
import numpy as np

def quantile_1_golden():


    probs = [0.01, 0.05, 0.1, 0.25, 0.333, 0.5, 0.667, 0.75, 0.9, 0.95, 0.99]

    vec = [random.gauss(0,1) for i in range(1000)]
    vec_h2o = h2o.H2OFrame(vec)

    print "Check errors generated for probabilities outside [0,1]"
    try:
        print vec_h2o.quantile(prob=[-0.2])
        assert False, "Expected error. Probabilities must be between 0 and 1"
    except EnvironmentError:
        assert True

    try:
        print vec_h2o.quantile(prob=[1.2])
        assert False, "Expected error. Probabilities must be between 0 and 1"
    except EnvironmentError:
        assert True

    try:
        print vec_h2o.quantile(prob=[0.1, -0.5, 0.2, 1.5])
        assert False, "Expected error. Probabilities must be between 0 and 1"
    except EnvironmentError:
        assert True

    print "Check min/max equal to 0% and 100% quantiles"
    q_min = vec_h2o.quantile(prob=[0])[0,1]
    h2o_min = vec_h2o.min()
    assert abs(q_min - h2o_min) < 1e-8, "Expected minimum value of {0} but got {1}".format(h2o_min, q_min)

    q_max = vec_h2o.quantile(prob=[1])[0,1]
    h2o_max = vec_h2o.max()
    assert abs(q_max - h2o_max) < 1e-8, "Expected minimum value of {0} but got {1}".format(h2o_max, q_max)

    print "Check constant vector returns constant for all quantiles"
    vec_cons = [[5] for i in range(1000)]
    vec_cons_h2o = h2o.H2OFrame(vec_cons)

    res = vec_cons_h2o.quantile(prob=probs)
    for r in range(len(res[0])):
        val = res[r,1]
        assert val == 5, "Expected value of {0} but got {1}".format(5, val)

    print "Check missing values are ignored in calculation"
    vec_na_h2o = [random.gauss(0,1) if random.uniform(0,1) > 0.1 else None for i in range(1000)]
    vec_na_np = []
    for v in vec_na_h2o:
        if v is not None: vec_na_np.append(v)

    h2o_data = h2o.H2OFrame(vec_na_h2o)
    np_data = np.array(vec_na_np)

    h2o_quants = h2o_data.quantile(prob=probs)
    np_quants = np.percentile(np_data,[1, 5, 10, 25, 33.3, 50, 66.7, 75, 90, 95, 99],axis=0)

    for e in range(9):
        h2o_val = h2o_quants[e,1]
        np_val = np_quants[e]
        assert abs(h2o_val - np_val) < 1e-08, \
            "check unsuccessful! h2o computed {0} and numpy computed {1}. expected equal quantile values between h2o " \
            "and numpy".format(h2o_val,np_val)




if __name__ == "__main__":
    pyunit_utils.standalone_test(quantile_1_golden)
else:
    quantile_1_golden()
