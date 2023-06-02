import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
import math

def test_vif_tweedie_CV():
    nfold = 3
    training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv")
    Y = 'resp'
    x = ['abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.']
    model = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, generate_variable_inflation_factors=True,
                                          fold_assignment='modulo', nfolds = nfold, keep_cross_validation_models=True)
    model.train(training_frame=training_data, x=x, y=Y)

    # create a fold column for train
    fold_numbers = training_data.modulo_kfold_column(n_folds=3)
    # rename the column "fold_numbers"
    fold_numbers.set_names(["fold_numbers"])
    train = training_data.cbind(fold_numbers)
    model_fold_col = H2OGeneralizedLinearEstimator(family='gamma', lambda_=0, generate_variable_inflation_factors=True, 
                                                   fold_column="fold_numbers", keep_cross_validation_models=True)
    model_fold_col.train(training_frame=train, x=x, y=Y)
    # check cross validation models from both GLM models should be the same
    xval_models =  model.get_xval_models()
    xval_models_fold_col = model_fold_col.get_xval_models()
    # compare VIF from cross-validation models and make sure they are the same
    for index in range(0, nfold):
        assertEqualVIF(xval_models[index].get_variable_inflation_factors(), 
                       xval_models_fold_col[index].get_variable_inflation_factors())
        
    # check that variable inflation factors from both GLM models
    assertEqualVIF(model.get_variable_inflation_factors(), model_fold_col.get_variable_inflation_factors())
            
def assertEqualVIF(vif1, vif2):
    keys = vif1.keys()
    for key in keys:
        if (math.isnan(vif1[key])):
            assert math.isnan(vif2[key])
        else:
            assert abs(vif1[key]-vif2[key]) < 1e-6, "Expected VIF: {0}, Actual VIF: {1}.  They are " \
                                                       "different".format(vif1[key], vif2[key])

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_vif_tweedie_CV)
else:
    test_vif_tweedie_CV()
