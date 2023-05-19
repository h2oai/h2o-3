from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check and make sure that we can use the knot key to assign knots locations.
def test_gam_knots_key():
    print("Checking coefficients and variable importance for multinomial")
    knots1 = [-1.9990569949269443, -0.9814307533427584, 0.025991586992542004, 1.0077098743127828, 1.999422899675758]
    frameKnots1 = h2o.H2OFrame(python_obj=knots1)
    knots2 = [-1.999821860724304, -1.005257990219052, -0.006716041928736871, 1.002197392215352, 1.9990735891238567]
    frameKnots2 = h2o.H2OFrame(python_obj=knots2)
    knots3 = [-1.9996756878575048, -0.979893795962986, 0.007573326860148999, 1.011437346619421, 1.9996116758461224]
    frameKnots3 = h2o.H2OFrame(python_obj=knots3)
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    y = "C11"
    x=["C1","C2"]
    h2o_data["C11"] = h2o_data["C11"].asfactor()  
    numKnots = [5,5,5]
    h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"],  scale = [1,1,1], 
                                                num_knots=numKnots, bs = [3,3,3], knot_ids=[frameKnots1.key, frameKnots2.key, 
                                                                                frameKnots3.key], seed=1234)
    h2o_model.train(x=x, y=y, training_frame=h2o_data)
    h2oCoeffs = h2o_model.coef()
    h2o_model2 = H2OGeneralizedAdditiveEstimator(family='multinomial', gam_columns=["C6","C7","C8"],  scale = [1,1,1],
                                                bs=[3,3,3], num_knots=numKnots, seed=1234)
    h2o_model2.train(x=x, y=y, training_frame=h2o_data)
    h2oCoeffs2 = h2o_model2.coef()

    keyNames = h2oCoeffs["coefficients"].keys()
    for kNames in keyNames:
        diff = abs(h2oCoeffs["coefficients"][kNames]-h2oCoeffs2["coefficients"][kNames])
        assert diff < 1e-2, "expected coefficients: {0}.  actual coefficients: {1}".format(h2oCoeffs["coefficients"][kNames], h2oCoeffs2["coefficients"][kNames])
    print("gam knot keys test completed successfully")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_knots_key)
else:
    test_gam_knots_key()
