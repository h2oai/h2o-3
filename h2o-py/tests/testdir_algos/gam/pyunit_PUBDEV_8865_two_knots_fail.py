import sys, os
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# test taken from Narasimha Durgam
def test_2_knots():
    train = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/gam_test/dataset-37830.csv")
    y = "wage"
    x = ["year","age"]

    # build the GAM model
    h2o_model = H2OGeneralizedAdditiveEstimator(family='gaussian', bs=[2], gam_columns=["age"], seed=9, 
                                                spline_orders = [1], num_knots = [2])
    h2o_model.train(x=x, y=y, training_frame=train)
    # get the model coefficients
    h2oCoeffs = h2o_model.coef()
    age_is_0_val = h2oCoeffs["age_is_0"]
    age_is_0_val_table = h2o_model._model_json["output"]["coefficients_table_no_centering"]._cell_values[2][1]
    assert abs(age_is_0_val-age_is_0_val_table) < 1e-6, "expected gam spline coefficient: {0}, actual: " \
                                                        "{1}".format(age_is_0_val_table, age_is_0_val)
    h2oCoeffs

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_2_knots)
else:
    test_2_knots()
