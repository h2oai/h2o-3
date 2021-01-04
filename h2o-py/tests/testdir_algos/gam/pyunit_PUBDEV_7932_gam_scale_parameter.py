from __future__ import division
from __future__ import print_function
from past.utils import old_div
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# In this test, we check and make sure that the scale parameter is applied properly.  Here, we check and make sure
# the mse or logloss obtained from two models built with different scale parameters should be different.
def test_gam_scale_parameters():
    print("Checking logloss for binomial with different scale parameters")
    h2o_data = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    buildModelScaleParam(h2o_data, myY, ["C11", "C12", "C13"], 'binomial')

    print("Checking mse for gaussian with different scale parameters")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C21"
    buildModelScaleParam(h2o_data, myY, ["C11", "C12", "C13"], 'gaussian')

    print("Checking logloss for multinomial with different scale parameters")
    h2o_data = h2o.import_file(
    path=pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    myY = "C11"
    h2o_data["C11"] = h2o_data["C11"].asfactor()
    buildModelScaleParam(h2o_data, myY, ["C6", "C7", "C8"], 'multinomial')
    print("gam scale parameter test completed successfully")    


def buildModelScaleParam(train_data, y, gamX, family):
    numKnots = [5,6,7]
    x=["C1","C2"]
    h2o_model = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX,  scale = [0.001, 0.001, 0.001], num_knots=numKnots)
    h2o_model.train(x=x, y=y, training_frame=train_data)   
    h2o_model2 = H2OGeneralizedAdditiveEstimator(family=family, gam_columns=gamX,  scale = [10, 10, 10], num_knots=numKnots)
    h2o_model2.train(x=x, y=y, training_frame=train_data)
    if family == 'multinomial' or family == 'binomial':
        logloss1 = h2o_model.logloss()
        logloss2 = h2o_model2.logloss()
        assert not(logloss1 == logloss2), "logloss from models with different scale parameters should be different but is not."
    else:
        mse1 = h2o_model.mse()
        mse2 = h2o_model2.mse()
        assert not(mse1 == mse2), "mse from models with different scale parameters should be different but is not."
        

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_scale_parameters)
else:
    test_gam_scale_parameters()
