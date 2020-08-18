from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we want to make sure the correct error message is returned when a gam column has two
# few values to be divided into bins and when the knot locations are not increasing in value.
def test_gam_knots_key():
    # bad gam_column with not enough values is chosen to be gam_column
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
    h2o_data.head()
    myY = "CAPSULE"
    myX = ["ID","AGE","RACE","GLEASON","DCAPS","PSA","VOL","DPROS"]
    h2o_data[myY] = h2o_data[myY].asfactor()
    try:
        h2o_model = H2OGeneralizedAdditiveEstimator(family='binomial', gam_columns=["GLEASON"])
        h2o_model.train(x=myX, y=myY, training_frame=h2o_data)
        assert False, "Should have throw exception due to bad gam_column choice"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert "does have not enough values to generate well-defined knots" in temp, "wrong error message received."

    # knots not chosen in ascending error and corresponding error message
    knots1 = [-0.98143075, -1.99905699,  0.02599159, 1.00770987, 1.99942290]
    frameKnots1 = h2o.H2OFrame(python_obj=knots1)

    try:
        h2o_model = H2OGeneralizedAdditiveEstimator(family='binomial', gam_columns=["GLEASON"], 
                                                    knot_ids = [frameKnots1.key])
        h2o_model.train(x=myX, y=myY, training_frame=h2o_data)
        assert False, "Should have throw exception due to bad knot location choices"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert "knots not sorted in ascending order for gam_column" in temp, "wrong error message received."

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_knots_key)
else:
    test_gam_knots_key()
