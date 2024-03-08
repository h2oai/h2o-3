import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we test when we specify I-spline, the solver is not chosen, an error should appear.
def test_solver_monotone_splines():
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
    myY = "CAPSULE"
    myX = ["ID","AGE","RACE","GLEASON","DCAPS","PSA","VOL","DPROS"]
    h2o_data[myY] = h2o_data[myY].asfactor()
    try:
        h2o_model = H2OGeneralizedAdditiveEstimator(family='binomial', gam_columns=["GLEASON"], bs=[2])
        h2o_model.train(x=myX, y=myY, training_frame=h2o_data)
        assert False, "Should have throw exception due to solver not set to IRLSM"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert "solver:  for monotone spline (bs=2), must choose irlsm as solver." in temp, "wrong error message received."



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_solver_monotone_splines)
else:
    test_solver_monotone_splines()
