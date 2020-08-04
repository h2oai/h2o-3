from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator


# In this test, we check and make sure that we can do scoring
def test_gam_model_predict():
    print("Checking model scoring for gaussian")
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    model_test_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/predictGaussianGAM2.csv"))
    buildModelCheckPredict(h2o_data, h2o_data,  model_test_data, myY, ["C11", "C12", "C13"], 'gaussian', 'gaussian')
    pred_gauss = buildModelCheckPredict(h2o_data, h2o_data,  model_test_data, myY, ["C11", "C12", "C13"], 'gaussian', 'gaussian')
    pred_auto_gauss = buildModelCheckPredict(h2o_data, h2o_data,  model_test_data, myY, ["C11", "C12", "C13"], 'AUTO', 'gaussian')
    pyunit_utils.compare_frames_local(pred_gauss, pred_auto_gauss, prob=1)

    print("Checking model scoring for multinomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C11"
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    model_test_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/predictMultinomialGAM2.csv"))
    pred_multi = buildModelCheckPredict(h2o_data, h2o_data, model_test_data, myY, ["C6", "C7", "C8"], 'multinomial', 'multinomial')
    pred_auto_multi = buildModelCheckPredict(h2o_data, h2o_data, model_test_data, myY, ["C6", "C7", "C8"], 'AUTO', 'multinomial')
    pyunit_utils.compare_frames_local(pred_multi, pred_auto_multi, prob=1)

    print("Checking model scoring for binomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    model_test_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/predictBinomialGAM2.csv"))
    pred_bin = buildModelCheckPredict(h2o_data, h2o_data, model_test_data, myY, ["C11", "C12", "C13"], 'binomial', 'binomial')
    pred_auto_bin = buildModelCheckPredict(h2o_data, h2o_data, model_test_data, myY, ["C11", "C12", "C13"], 'AUTO', 'binomial')
    pyunit_utils.compare_frames_local(pred_bin, pred_auto_bin, prob=1)
    print("gam coeff/varimp test completed successfully")
    
    # add fractional binomial just to make sure it runs
    print("Checking model scoring for fractionalbinomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_model = H2OGeneralizedAdditiveEstimator(family="fractionalbinomial", gam_columns=["C11", "C12", "C13"],  
                                                scale = [1,1,1], num_knots=[5,5,5],standardize=True,solver="irlsm")
    h2o_model.train(x=["C1","C2"], y="C21", training_frame=h2o_data)
    predictTest = h2o_model.predict(h2o_data)
    # okay not to have assert/compare here


def buildModelCheckPredict(train_data, test_data, model_test_data, myy, gamX, family, actual_family):
    numKnots = [5,5,5]
    x=["C1","C2"]
   
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX,  scale = [1,1,1], num_knots=numKnots, 
                                                standardize=True, Lambda=[0], alpha=[0], max_iterations=3, 
                                                compute_p_values=False, solver="irlsm")
    h2o_model.train(x=x, y=myy, training_frame=train_data)
    pred = h2o_model.predict(test_data)
    if pred.ncols < model_test_data.ncols:
        ncolT = model_test_data.ncols-1
        model_test_data = model_test_data.drop(ncolT)
    if (family == 'gaussian' or (family == 'AUTO' and actual_family == 'gaussian')):
        pyunit_utils.compare_frames_local(pred, model_test_data, prob=1)
    else:
        pred = pred.drop('predict')
        model_test_data = model_test_data.drop('predict')
        pyunit_utils.compare_frames_local(pred, model_test_data, prob=1)
    return pred


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
