from __future__ import division
from __future__ import print_function
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
    model_test_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/predictBinomialGAM.csv"))
    buildModelMetricsCheck(h2o_data, h2o_data, model_test_data, myY, ["C11", "C12", "C13"], 'binomial')

    print("Checking modelmetrics for gaussian")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    model_test_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/predictGaussianGAM.csv"))
    buildModelMetricsCheck(h2o_data, h2o_data,  model_test_data, myY, ["C11", "C12", "C13"], 'gaussian')

    print("Checking modelmetrics for multinomial")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C11"
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    model_test_data = h2o.import_file(pyunit_utils.locate("smalldata/gam_test/predictMultinomialGAM.csv"))
    buildModelMetricsCheck(h2o_data, h2o_data, model_test_data, myY, ["C6", "C7", "C8"], 'multinomial')
    
    print("gam modelmetrics test completed successfully")    
    
def buildModelMetricsCheck(train_data, test_data, model_test_data, y, gamX, family):
    numKnots = [5,6,7]
    x=["C1","C2"]
    numCoeffs = len(train_data["C1"].categories())+len(train_data["C2"].categories())+sum(numKnots)+1-len(numKnots)
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_x=gamX,  scale = [1,1,1], k=numKnots, 
                                                standardize=True, Lambda=[0], alpha=[0], max_iterations=3)
    h2o_model.train(x=x, y=y, training_frame=train_data)
    if family=='binomial':
        h2o_model.auc()
        h2o_model.aic()
        h2o_model.logloss()
        h2o_model.null_deviance()
        h2o_model.residual_deviance()
    elif family=='multinomial':
        h2o_model.null_deviance()
        h2o_model.residual_deviance()
    else:
        h2o_model.mse()
        h2o_model.null_deviance()
        h2o_model.residual_deviance()
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_modelMetrics)
else:
    test_gam_modelMetrics()
