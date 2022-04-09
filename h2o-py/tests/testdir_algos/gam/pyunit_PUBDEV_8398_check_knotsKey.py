from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check and make sure that we can use the knot key to assign knots locations.
def test_gam_knots_key():
    print("Checking coefficients and variable importance for multinomial")
    knots1 = [-49.98693927762423, -25.286098564527954, 0.44703511170863297, 25.50661829462607, 49.97312855846752]
    frameKnots1 = h2o.H2OFrame(python_obj=knots1)
    knots2 = [-49.99386508664034, -25.275868426388616, 0.012500153211602433, 25.13371167580791, 49.98738587466542]
    frameKnots2 = h2o.H2OFrame(python_obj=knots2)
    knots3 = [-49.99241697497996, -24.944012655490237, 0.1578389050436152, 25.296897954643736, 49.9876932143425]
    frameKnots3 = h2o.H2OFrame(python_obj=knots3)
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    y = "C21"
    x=["C1","C2"]
    numKnots = [5,5,5]
    h2o_model = H2OGeneralizedAdditiveEstimator(family='gaussian', gam_columns=["C11","C12","C13"],  scale = [1,1,1], 
                                                num_knots=numKnots, bs=[2, 2, 0], seed=12345,
                                                knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key])
    h2o_model.train(x=x, y=y, training_frame=h2o_data)
    h2oCoeffs = h2o_model.coef()
    h2o_model2 = H2OGeneralizedAdditiveEstimator(family='gaussian', gam_columns=["C11","C12","C13"],  scale = [1,1,1],
                                                 num_knots=numKnots, bs=[2, 2, 0], seed=12345)
    h2o_model2.train(x=x, y=y, training_frame=h2o_data)
    h2oCoeffs2 = h2o_model2.coef()

    keyNames = h2oCoeffs.keys()
    for kNames in keyNames:
        assert abs(h2oCoeffs[kNames]-h2oCoeffs2[kNames]) < 1e-6, "expected coefficients: {0}.  actual coefficients: " \
                                                                 "{1}".format(h2oCoeffs[kNames], h2oCoeffs2[kNames])
    print("gam knot keys test completed successfully")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_knots_key)
else:
    test_gam_knots_key()
