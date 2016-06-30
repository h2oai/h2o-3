from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def test_relevel():
    #First, compare againts itself
    print("Importing prostate_cat.csv data...\n")
    d = h2o.import_file(path = pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"), na_strings=["NA","NA","NA","NA","NA","NA","NA","NA"])

    mh2o1 = H2OGeneralizedLinearEstimator(family = "binomial", Lambda=0, missing_values_handling = "Skip")
    mh2o1.train(x=list(range(1, d.ncol)), y=0, training_frame=d)
    ns = mh2o1.coef().keys()
    print(ns)
    assert("DPROS.None" in ns, "None level IS NOT expected to be skipped by default")
    assert(("DPROS.Both" not in ns), "Both level IS expected to be skipped by default")
    x = d["DPROS"].relevel("None")
    print(x)
    d["DPROS"] = x[0]

    mh2o2 = H2OGeneralizedLinearEstimator(family = "binomial", Lambda=0, missing_values_handling = "Skip")
    mh2o2.train(x=list(range(1, d.ncol)), y=0, training_frame=d)
    ns2 = mh2o2.coef().keys()
    print(ns2)
    assert("DPROS.None" in ns2, "None level IS NOT expected to be skipped by default")
    assert(("DPROS.Both" not in ns2), "Both level IS expected to be skipped by default")

    #Second, compare against R input (taken from runit_relevel.R)
    dr = h2o.import_file(path = pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    dr["DPROS"] = d["DPROS"].relevel("None")
    #Results are from R but manualy reordered and renamed to match h2o naming and order
    exp_coefs = {"Intercept": -7.63245 , "DPROS.Both": 1.39185, "DPROS.Left": 0.73482, "DPROS.Right": 1.51437, "RACE.White": 0.65160, "DCAPS.Yes": 0.49233,
                 "AGE":-0.01189 , "PSA": 0.02990, "VOL": -0.01141, "GLEASON": 0.96466927}
    coeff_diff = {key: abs(exp_coefs[key] - mh2o2.coef().get(key, 0)) for key in exp_coefs.keys()}
    assert (max(coeff_diff.values()) < 1e-4)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_relevel)
else:
    test_relevel()

