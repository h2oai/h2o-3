from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check and make sure that we can use beta constraints with IS
def test_gam_beta_constraints():
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    bc = []
    bc.append(["C1", 0.0, 0.5])
    bc.append(["C13", 0.0, 0.7])
    beta_constraints = h2o.H2OFrame(bc)
    beta_constraints.set_names(["names", "lower_bounds", "upper_bounds"])
    y = "C21"
    x=["C1","C2","C13"]
    numKnots = [5,5,5]
    h2o_model = H2OGeneralizedAdditiveEstimator(family='gaussian', gam_columns=["C11","C12","C13"],  scale = [1,1,1], 
                                                num_knots=numKnots, bs=[2, 2, 0],beta_constraints=beta_constraints,
                                                seed=12)
    h2o_model.train(x=x, y=y, training_frame=h2o_data)
    h2oCoeffs = h2o_model.coef()
    h2o_model2 = H2OGeneralizedAdditiveEstimator(family='gaussian', gam_columns=["C11","C12","C13"],  scale = [1,1,1],
                                                 num_knots=numKnots, bs=[2, 2, 0],beta_constraints=beta_constraints,
                                                 seed=12)
    h2o_model2.train(x=x, y=y, training_frame=h2o_data)
    h2oCoeffs2 = h2o_model2.coef()

    keyNames = h2oCoeffs.keys()
    for kNames in keyNames:
        assert abs(h2oCoeffs[kNames]-h2oCoeffs2[kNames]) < 1e-6, \
            "expected coefficients: {0}.  actual coefficients: {1}".format(h2oCoeffs[kNames], h2oCoeffs2[kNames])
        # check to make sure gam column coefficients are non-negative
    coef_dict = h2o_model.coef()
    coef_keys = coef_dict.keys()
    for key in coef_keys:
        if "_is_" in key:
            assert coef_dict[key] >= 0

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_beta_constraints)
else:
    test_gam_beta_constraints()
