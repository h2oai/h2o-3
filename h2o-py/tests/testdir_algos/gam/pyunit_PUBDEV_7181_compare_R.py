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
    assert gaussModelLS.rmse()*2 < rmseR, "H2O GAM mean residual error {0} is higher than R GAM mean residual " \
                                          "error {1}".format(gaussModelLS.rmse()*2, rmseR)
   
    print("Checking model scoring for binomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    rpredAcc = 6931.0/h2o_data.nrow
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    binomialModel = buildModelCheckPredict(h2o_data, myY, ["C11", "C12", "C13"], 'binomial')
    binomialModelLS = buildModelCheckPredict(h2o_data, myY, ["C11", "C12", "C13"], 'binomial', searchLambda = True)
    predF = binomialModel.predict(h2o_data)
    predFLS = binomialModelLS.predict(h2o_data)
    tempV = predF["predict"]==h2o_data["C21"]
    binomialAcc = tempV.sum()/h2o_data.nrow
    binomialAcc = float(binomialAcc.as_data_frame(use_pandas=False)[1][0])
    tempV3 = predFLS["predict"]==h2o_data["C21"]
    binomialLSAcc = tempV3.sum()/h2o_data.nrow
    binomialLSAcc = float(binomialLSAcc.as_data_frame(use_pandas=False)[1][0])
    print("R prediction accuracy: {0}.  H2O GAM prediction accuracy with mostly default settings: {1}.  H2O GAM "
          "prediction accuracy with Lambda search enabled: {2} ".format(rpredAcc, binomialAcc, binomialLSAcc))
    assert binomialLSAcc > rpredAcc, "H2O Gam accuracy: {0} is lower than R Gam accuracy: {1}".format(binomialLSAcc,
                                                                                                      rpredAcc)
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
