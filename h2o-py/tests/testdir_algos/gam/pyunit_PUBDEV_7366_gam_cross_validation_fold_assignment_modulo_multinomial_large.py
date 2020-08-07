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
    covtype_df = h2o.import_file(pyunit_utils.locate("bigdata/laptop/covtype/covtype.full.csv"))
    train, valid = covtype_df.split_frame([0.9], seed=1234)

    #Prepare predictors and response columns
    covtype_X = covtype_df.col_names[:-1]     #last column is Cover_Type, our desired response variable 
    covtype_y = covtype_df.col_names[-1]
    # build model with cross validation and no validation dataset
    gam_multi = H2OGeneralizedAdditiveEstimator(family='multinomial', solver='IRLSM', gam_columns=["Slope"], 
                                                scale = [0.0001], num_knots=[5], standardize=True, nfolds=2, 
                                                fold_assignment = 'modulo', alpha=[0.9,0.5,0.1], lambda_search=True,
                                                nlambdas=5, max_iterations=3)
    gam_multi.train(covtype_X, covtype_y, training_frame=train)
    # build model with cross validation and with validation dataset
    gam_multi_valid = H2OGeneralizedAdditiveEstimator(family='multinomial', solver='IRLSM', gam_columns=["Slope"],
                                                scale = [0.0001], num_knots=[5], standardize=True, nfolds=2,
                                                fold_assignment = 'modulo', alpha=[0.9,0.5,0.1], lambda_search=True,
                                                nlambdas=5, max_iterations=3)
    gam_multi_valid.train(covtype_X, covtype_y, training_frame=train, validation_frame=valid)
    # model should yield the same coefficients in both case
    gam_multi_coef = gam_multi.coef()
    gam_multi_valid_coef = gam_multi_valid.coef()
    pyunit_utils.assertEqualCoeffDicts(gam_multi_coef['coefficients'], gam_multi_valid_coef['coefficients'])
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
