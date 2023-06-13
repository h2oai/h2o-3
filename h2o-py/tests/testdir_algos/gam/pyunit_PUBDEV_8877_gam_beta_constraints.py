from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check and make sure splines_non_negative should take precedence over the non_negative parameter
def tea_gam_non_negative_spline_non_negative():
    train1 = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/nondecreasingCosTest.csv"))
    gamX = ["a"]
    x = ["a"]
    numKnots = [4]
    scale= [0.001]
    bs_type = [2]
    splines_non_negative = [False]
    spline_order = [3]
    # build GAM with non_negative set to True but splines_non_negative set to False.  The I-spline specification 
    # should take precedence
    h2o_model = H2OGeneralizedAdditiveEstimator(gam_columns=gamX, scale=scale, bs=bs_type, spline_orders=spline_order,
                                                num_knots=numKnots,  seed=12345, non_negative = True, 
                                                splines_non_negative=splines_non_negative)
    h2o_model.train(x = x, y="cosy", training_frame=train1)
    # check that all coefficients are positive except for the ones for I-spline that is set to be non-positive
    coefs = h2o_model.coef()
    del coefs["Intercept"]
    non_positive_names = ["a_is_0", "a_is_1", "a_is_2", "a_is_3", "a_is_4"]
    for key in coefs.keys():
        if key in non_positive_names:
            assert coefs[key] <= 0, "Expected gamified coefficient to be non-positive.  But actual is : {0}".format(coefs[key])
        else:
            assert coefs[key] >= 0, "Expected coefficient to be non-negative.  But actual is : {0}".format(coefs[key])  

if __name__ == "__main__":
    pyunit_utils.standalone_test(tea_gam_non_negative_spline_non_negative)
else:
    tea_gam_non_negative_spline_non_negative()
