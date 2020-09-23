from __future__ import division
from __future__ import print_function
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import random

# In this test, I will test all the following cross-validation parameters:
# 1. fold_assignment = random
# 2. keep_cross_validation_model
# 3. keep_cross_validation_predictions
# 4. keep_cross_validation_fold_assignment
# 
# If we keep the cross-validation models and the fold assignment, then the prediction using the folds and
# the predictions kept from cross-validation should yield the same result!
def test_gam_model_predict():
    print("Checking cross validation for GAM binomial")
    print("Preparing for data....")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C3"] = h2o_data["C3"].asfactor()
    h2o_data["C4"] = h2o_data["C4"].asfactor()
    h2o_data["C5"] = h2o_data["C5"].asfactor()
    h2o_data["C6"] = h2o_data["C6"].asfactor()
    h2o_data["C7"] = h2o_data["C7"].asfactor()
    h2o_data["C8"] = h2o_data["C8"].asfactor()
    h2o_data["C9"] = h2o_data["C9"].asfactor()
    h2o_data["C10"] = h2o_data["C10"].asfactor()
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()

    nfold = random.randint(3,8)
    h2o_model = H2OGeneralizedAdditiveEstimator(family = 'binomial', gam_columns = ["C11"],  scale = [0.0001],
                                                nfolds = nfold,
                                                keep_cross_validation_models = True,
                                                keep_cross_validation_predictions = True,
                                                keep_cross_validation_fold_assignment = True,
                                                fold_assignment = "random")
    h2o_model.train(x=list(range(0,20)), y=myY, training_frame=h2o_data)
    xval_models = h2o_model.get_xval_models()
    assert len(xval_models)==nfold, "expected {0} models but received {1} models".format(nfold, len(xval_models))
    xval_predictions = h2o_model.cross_validation_holdout_predictions()
    xval_fold_assignments = h2o_model.cross_validation_fold_assignment()
    assert xval_fold_assignments.max() == (nfold-1), "expected fold_assignment max: {0}, actual max: " \
                                                 "{1}".format(nfold-1, xval_fold_assignments.max())
    assert xval_predictions.nrow == h2o_data.nrow, "expected fold_assignment row size: {0}, actual row size: " \
                                                   "{1}".format(h2o_data.nrow, xval_predictions.nrow)


    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_model_predict)
else:
    test_gam_model_predict()
