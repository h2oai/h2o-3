from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check and make sure that we can do scoring
def test_gam_model_predict():
    print("Checking model scoring for gaussian")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    model_test_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/predictGaussianGAM.csv"))
    buildModelCheckPredict(h2o_data, h2o_data,  model_test_data, myY, ["C11", "C12", "C13"], 'gaussian')

    print("Checking model scoring for multinomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C11"
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    model_test_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/predictMultinomialGAM.csv"))
    buildModelCheckPredict(h2o_data, h2o_data, model_test_data, myY, ["C6", "C7", "C8"], 'multinomial')


    print("Checking model scoring for binomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    model_test_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/predictBinomialGAMRPython.csv"))
    buildModelCheckPredict(h2o_data, h2o_data, model_test_data, myY, ["C11", "C12", "C13"], 'binomial')
    print("gam coeff/varimp test completed successfully")    
    
def buildModelCheckPredict(train_data, test_data, model_test_data, myy, gamX, family):
    numKnots = [5,5,5]
    x=["C1","C2"]
   
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_x=gamX,  scale = [1,1,1], k=numKnots, 
                                                standardize=True, Lambda=[0], alpha=[0], max_iterations=3, 
                                                compute_p_values=False, solver="irlsm")
    h2o_model.train(x=x, y=myy, training_frame=train_data)
    pred = h2o_model.predict(test_data)
    if pred.ncols < model_test_data.ncols:
        ncolT = model_test_data.ncols-1
        model_test_data = model_test_data.drop(ncolT)
    pyunit_utils.compare_frames_local(pred, model_test_data)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
