from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check to make sure scoring works
def test_gam_nonNeg_coeffs():
    print("Checking logloss for binomial with different scale parameters")
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    buildModelCheckCoeff(h2o_data, myY, ["C11", "C12", "C13"], [3,4,5], 'binomial')

    print("Checking mse for gaussian with different scale parameters")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    buildModelCheckCoeff(h2o_data, myY, ["C11", "C12", "C13"], [2,3,4], 'gaussian')

    print("Checking logloss for multinomial with different scale parameters")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C11"
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    buildModelCheckCoeff(h2o_data, myY, ["C6", "C7", "C8"], [1,2,3], 'multinomial')
    print("gam scale parameter test completed successfully")    


def buildModelCheckCoeff(train_data, y, gamX, spline_order, family):
    numKnots = [3,4,5]
    scale= [0.001, 0.001, 0.001]
    bs_type = [2,2,2]
    x=["C1","C2"]
    frames = train_data.split_frame(ratios=[0.9])
    train_part = frames[0]
    test_part = frames[1]
    # building multiple models with same training / test datasets to make sure it works
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX, scale=scale, bs=bs_type, 
                                                spline_orders=spline_order, num_knots=numKnots)
    h2o_model.train(x=x, y=y, training_frame=train_part, validation_frame=test_part)
    predict_frame = h2o_model.predict(test_part)
    h2o_model2 = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX, scale=scale,
                                                bs=bs_type, spline_orders=spline_order,num_knots=numKnots)
    h2o_model2.train(x=x, y=y, training_frame=train_part, validation_frame=test_part)
    predict_frame2 = h2o_model2.predict(test_part)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_nonNeg_coeffs)
else:
    test_gam_nonNeg_coeffs()
