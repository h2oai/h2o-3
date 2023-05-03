import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check and make sure that we can do scoring
def test_gam_modelMetrics():
    print("Checking modelmetrics for binomial")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    buildModelMetricsCheck(h2o_data, myY, ["C11", "C12", "C13"], 'binomial')

    print("Checking modelmetrics for gaussian")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    buildModelMetricsCheck(h2o_data, myY, ["C11", "C12", "C13"], 'gaussian')
    
    print("gam modelmetrics test completed successfully")    
    
def buildModelMetricsCheck(train_data, y, gamX, family):
    numKnots = [5,6,7]
    x=["C1","C2"]
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX,  scale = [1,1,1], num_knots=numKnots, 
                                                standardize=True, Lambda=[0], alpha=[0], max_iterations=3, bs=[0,2,3])
    h2o_model.train(x=x, y=y, training_frame=train_data)
    if family=='binomial':
        h2o_model.auc()
        h2o_model.aic()
        h2o_model.logloss()
        h2o_model.null_deviance()
        h2o_model.residual_deviance()
        assert h2o_model.residual_deviance() != None
    else:
        h2o_model.mse()
        h2o_model.null_deviance()
        h2o_model.residual_deviance()
        assert h2o_model.residual_deviance() != None
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_modelMetrics)
else:
    test_gam_modelMetrics()
