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
    train1 = prepareData("smalldata/glm_test/gaussian_20cols_10000Rows.csv")
    train2 = prepareData("smalldata/glm_test/gaussian_20cols_10000Rows.csv")
    knots1 = [-49.98693927762423, 0.44703511170863297, 49.97312855846752]
    frameKnots1 = h2o.H2OFrame(python_obj=knots1)
    knots2 = [-49.99386508664034, -16.6904002652171, 16.298265682961386, 49.98738587466542]
    frameKnots2 = h2o.H2OFrame(python_obj=knots2)
    knots3 = [-49.99241697497996, -24.944012655490237, 0.1578389050436152, 25.296897954643736, 49.9876932143425]
    frameKnots3 = h2o.H2OFrame(python_obj=knots3)
    predict_frame = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/gaussian_20cols_10000Rows_predict4.csv"))
    gamX = ["C11", "C12", "C13"]
    x = train1.names
    x.remove("C21")
    predictors = [ele for ele in x if not(ele in gamX)]
    buildModelCheckCoeff(train1, train2, predict_frame, predictors, [frameKnots1.key, frameKnots2.key, frameKnots3.key],
                         "C21", gamX, [2,3,4], 'gaussian')

    train1 = prepareData("smalldata/glm_test/binomial_20_cols_10KRows.csv")
    train2 = prepareData("smalldata/glm_test/binomial_20_cols_10KRows.csv")
    predict_frame = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/binomial_20_cols_10KRows_predict4.csv"))
    train1["C21"] = train1["C21"].asfactor()
    train2["C21"] = train2["C21"].asfactor()
    gamX = ["C11", "C12", "C13"]
    x = train1.names
    x.remove("C21")
    predictors = [ele for ele in x if not(ele in gamX)]
    knots1 = [-1.999662934844682, -0.008421144219463272, 1.999459888241264]
    frameKnots1 = h2o.H2OFrame(python_obj=knots1)
    knots2 = [-1.9997515661922347, -0.6738945321313676, 0.6508273358344479, 1.9992225172848492]
    frameKnots2 = h2o.H2OFrame(python_obj=knots2)
    knots3 = [-1.999891109719008, -0.9927241013095163, 0.02801505726801068, 1.033088395720594, 1.9999726397518467]
    frameKnots3 = h2o.H2OFrame(python_obj=knots3)
    buildModelCheckCoeff(train1, train2, predict_frame, predictors, [frameKnots1.key, frameKnots2.key, frameKnots3.key],
                         "C21", gamX, [3,4,5], 'binomial')   

def prepareData(pathToFile):
    train_data = h2o.import_file(pyunit_utils.locate(pathToFile))
    train_data["C1"] = train_data["C1"].asfactor()
    train_data["C2"] = train_data["C2"].asfactor()
    train_data["C3"] = train_data["C3"].asfactor()
    train_data["C4"] = train_data["C4"].asfactor()
    train_data["C5"] = train_data["C5"].asfactor()
    return train_data

def buildModelCheckCoeff(train1, train2, predict_frame_correct, x, knotsIDs, y, gamX, spline_order, family):
    numKnots = [3,4,5]
    scale= [0.001, 0.001, 0.001]
    bs_type = [2,2,2]

    # building multiple models with same training / test datasets to make sure it works
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX, scale=scale, bs=bs_type, 
                                                keep_gam_cols=True, spline_orders=spline_order, num_knots=numKnots,
                                                seed=12345, knot_ids = knotsIDs)
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
