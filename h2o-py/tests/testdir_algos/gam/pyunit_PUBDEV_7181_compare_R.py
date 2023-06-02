import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
import math

# In this test, we compare our GAM with R Gam results
def test_gam_compare_R():
    print("Checking model scoring for gaussian")
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    rDeviance = 36138186581 # total squared residual error
    rmse = math.sqrt(rDeviance/h2o_data.nrow)
    
    gaussModel = buildModelCheckPredict(h2o_data, myY, ["C11", "C12", "C13"], 'gaussian', searchLambda = False)
    gaussModelLS = buildModelCheckPredict(h2o_data, myY, ["C11", "C12", "C13"], 'gaussian', searchLambda = True)
    print("R rmse is {0}.  H2O GAM rmse with mostly default settings: {1}.  H2O GAM rmse with Lambda search enabled: "
          "{2}".format(rmse, gaussModel.rmse(), gaussModelLS.rmse()))
    assert abs(min(gaussModelLS.rmse(),gaussModel.rmse()) - rmse)/max(min(gaussModelLS.rmse(),gaussModel.rmse()),
                                                                       rmse)<1e-1,\
        "H2O GAM square residual error {0} differs too much from R GAM square residual error " \
        "{1}".format(gaussModelLS.rmse(), rmse)
   
    print("Checking model scoring for binomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    rpredAcc = 6931.0/h2o_data.nrow
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    binomialModel = buildModelCheckPredict(h2o_data, myY, ["C11", "C12", "C13"], 'binomial', searchLambda=False, stdardize=True)
    binomialModelLS = buildModelCheckPredict(h2o_data, myY, ["C11", "C12", "C13"], 'binomial', searchLambda = True, stdardize=True)
    binomialAcc = binomialModel.accuracy()[0][1]
    binomialLSAcc = binomialModelLS.accuracy()[0][1]
    print("R prediction accuracy: {0}.  H2O GAM prediction accuracy with mostly default settings: {1}.  H2O GAM "
          "prediction accuracy with Lambda search enabled: {2} ".format(rpredAcc, binomialAcc, binomialLSAcc))
    assert max(binomialAcc,binomialLSAcc)>rpredAcc, "H2O Gam accuracy: {0} is lower than " \
                                                                  "R Gam accuracy: {1}".format(binomialLSAcc,rpredAcc)
    print("gam compare with R completed successfully")    
    
def buildModelCheckPredict(train_data, myy, gamX, family, searchLambda=False, stdardize=True):
    numKnots = [5,5,5]
    x=["C1","C2"]
   
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX,  scale = [0.1,0.1,0.1], 
                                                num_knots=numKnots, lambda_search = searchLambda, standardize=stdardize)
    h2o_model.train(x=x, y=myy, training_frame=train_data)
    return h2o_model

    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_compare_R)
else:
    test_gam_compare_R()
