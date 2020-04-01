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
    knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
    frameKnots1 = h2o.H2OFrame(python_obj=knots1)
    knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
    frameKnots2 = h2o.H2OFrame(python_obj=knots2)
    knots3 = [-1.999675688, -0.979893796, 0.007573327,1.011437347, 1.999611676]
    frameKnots3 = h2o.H2OFrame(python_obj=knots3)
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    y = "C11"
    x=["C1","C2"]
    h2o_data["C11"] = h2o_data["C11"].asfactor()  
    numKnots = [5,5,5]
    h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_x=["C6","C7","C8"],  scale = [1,1,1], 
                                                k=numKnots, knots_keys=[frameKnots1.key, frameKnots2.key, frameKnots3.key])
    h2o_model.train(x=x, y=y, training_frame=h2o_data)
    h2oCoeffs = h2o_model.coef()
    h2o_model2 = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_x=["C6","C7","C8"],  scale = [1,1,1],
                                                k=numKnots)
    h2o_model2.train(x=x, y=y, training_frame=h2o_data)
    h2oCoeffs2 = h2o_model2.coef()

    keyNames = h2oCoeffs["coefficients"].keys()
    for kNames in keyNames:
        assert abs(h2oCoeffs["coefficients"][kNames]-h2oCoeffs2["coefficients"][kNames]) < 1e-6, "expected coefficients: {0}.  actual coefficients: {1}".format(h2oCoeffs["coefficients"][kNames], h2oCoeffs2["coefficients"][kNames])
    print("gam knot keys test completed successfully")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_knots_key)
else:
    test_gam_knots_key()
