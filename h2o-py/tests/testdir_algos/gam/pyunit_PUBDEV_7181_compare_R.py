from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we compare our GAM with R Gam results
def test_gam_compare_R():
    print("Checking model scoring for gaussian")
    rmseR = 950.5063
    
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    gaussModel = buildModelCheckPredict(h2o_data, myY, ["C11", "C12", "C13"], 'gaussian', searchLambda = False)
    gaussModelLS = buildModelCheckPredict(h2o_data, myY, ["C11", "C12", "C13"], 'gaussian', searchLambda = True)
    print("R rmse is {0}.  H2O GAM rmse with mostly default settings: {1}.  H2O GAM rmse with Lambda search enabled: "
          "{2}".format(rmseR, gaussModel.rmse()*2, gaussModelLS.rmse()*2))
   
    print("Checking model scoring for binomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    rpredAcc = 6931.0/h2o_data.nrow
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    model_test_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/predictBinomialGAMRPython.csv")) 
    binomialModel = buildModelCheckPredict(h2o_data, myY, ["C11", "C12", "C13"], 'binomial')
    binomialModelLS = buildModelCheckPredict(h2o_data, myY, ["C11", "C12", "C13"], 'binomial', searchLambda = True)
    predF = binomialModel.predict(h2o_data)
    predFLS = binomialModelLS.predict(h2o_data)
    binomialAcc = (sum(predF["predict"]==h2o_data["C21"])[0,0])/h2o_data.nrow;
    binomialLSAcc = (sum(predFLS["predict"]==h2o_data["C21"])[0,0])/h2o_data.nrow;
    print("R prediction accuracy: {0}.  H2O GAM prediction accuracy with mostly default settings: {1}.  H2O GAM "
          "prediction accuracy with Lambda search enabled: {2} ".format(rpredAcc, binomialAcc, binomialLSAcc))
    print("gam compare with R completed successfully")    
    
def buildModelCheckPredict(train_data, myy, gamX, family, searchLambda=False):
    numKnots = [5,5,5]
    x=["C1","C2"]
   
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_x=gamX,  scale = [0.1,0.1,0.1], k=numKnots, 
                                                lambda_search = searchLambda)
    h2o_model.train(x=x, y=myy, training_frame=train_data)
    return h2o_model

    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_compare_R)
else:
    test_gam_compare_R()
