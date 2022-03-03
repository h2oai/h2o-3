from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check to make sure scoring with new dataset work properly
def test_gam_nonNeg_coeffs():
    print("Checking logloss for binomial with different scale parameters")
    train1 = prepareData("smalldata/glm_test/binomial_20_cols_10KRows.csv")
    train2 = prepareData("smalldata/glm_test/binomial_20_cols_10KRows.csv")
    predict_frame = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/binomial_20_cols_10KRows_predict.csv"))
    train1["C21"] = train1["C21"].asfactor()
    train2["C21"] = train2["C21"].asfactor()
    gamX = ["C11", "C12", "C13"]
    x = train1.names
    x.remove("C21")
    predictors = [ele for ele in x if not(ele in gamX)]
    buildModelCheckCoeff(train1, train2, predict_frame, predictors, "C21", gamX, [3,4,5], 'binomial')

    print("Checking mse for gaussian with different scale parameters")
    train1 = prepareData("smalldata/glm_test/gaussian_20cols_10000Rows.csv")
    train2 = prepareData("smalldata/glm_test/gaussian_20cols_10000Rows.csv")
    predict_frame = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/gaussian_20cols_10000Rows_predict.csv"))
    gamX = ["C11", "C12", "C13"]
    x = train1.names
    x.remove("C21")
    predictors = [ele for ele in x if not(ele in gamX)]
    buildModelCheckCoeff(train1, train2, predict_frame, predictors, "C21", gamX, [2,3,4], 'gaussian')
    print("gam scale parameter test completed successfully")    

def prepareData(pathToFile):
    train_data = h2o.import_file(pyunit_utils.locate(pathToFile))
    train_data["C1"] = train_data["C1"].asfactor()
    train_data["C2"] = train_data["C2"].asfactor()
    train_data["C3"] = train_data["C3"].asfactor()
    train_data["C4"] = train_data["C4"].asfactor()
    train_data["C5"] = train_data["C5"].asfactor()
    return train_data

def buildModelCheckCoeff(train1, train2, predict_frame_correct, x, y, gamX, spline_order, family):
#def buildModelCheckCoeff(train1, train2, x, y, gamX, spline_order, family):
    numKnots = [3,4,5]
    scale= [0.001, 0.001, 0.001]
    bs_type = [2,2,2]

    # building multiple models with same training / test datasets to make sure it works
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX, scale=scale, bs=bs_type, 
                                                spline_orders=spline_order, num_knots=numKnots, seed=12345)
    h2o_model.train(x=x, y=y, training_frame=train1, validation_frame=train2)
    predict_frame_test = h2o_model.predict(train2)
    if predict_frame_test.ncol > 1:
        pyunit_utils.compare_frames_local(predict_frame_correct[:,[1,2]], predict_frame_test[:, [1,2]], prob=1.0)
    else:
        pyunit_utils.compare_frames_local(predict_frame_correct, predict_frame_test, prob=1.0)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_nonNeg_coeffs)
else:
    test_gam_nonNeg_coeffs()
