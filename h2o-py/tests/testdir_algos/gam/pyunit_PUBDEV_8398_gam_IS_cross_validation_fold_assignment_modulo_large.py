from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator


# In this test, we check and make sure GAM supports cross-validation.  In particular, GAM should generate the same
# model if we chose fold_assignment = modulo, build the model with and without validation dataset
def test_gam_model_predict():
    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/backwardBinomial200C50KRows.csv"))
    valid = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/backwardBinomial200C50KRows.csv"))

    x = ["C1","C2","C3"]   
    y = "response"
    train[y] = train[y].asfactor()
    valid[y] = valid[y].asfactor()
    # build model with cross validation and with validation dataset
    gam_model_valid = H2OGeneralizedAdditiveEstimator(family='binomial', solver='IRLSM', gam_columns=["C4"],
                                                      scale = [0.0001], num_knots=[5], standardize=True, nfolds=2,
                                                      fold_assignment = 'modulo', alpha=[0.9,0.5,0.1], lambda_search=True,
                                                      nlambdas=5, max_iterations=3, bs=[2], seed=12345)
    gam_model_valid.train(x, y, training_frame=train, validation_frame=valid)
    # build model with cross validation and no validation dataset
    gam_model = H2OGeneralizedAdditiveEstimator(family='binomial', solver='IRLSM', gam_columns=["C4"],
                                                scale = [0.0001], num_knots=[5], standardize=True, nfolds=2,
                                                fold_assignment = 'modulo', alpha=[0.9,0.5,0.1], lambda_search=True,
                                                nlambdas=5, max_iterations=3, bs=[2], seed=12345)
    gam_model.train(x, y, training_frame=train)
    # model should yield the same coefficients in both case
    gam_model_coef = gam_model.coef()
    gam_model_valid_coef = gam_model_valid.coef()
    pyunit_utils.assertEqualCoeffDicts(gam_model_coef, gam_model_valid_coef)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
