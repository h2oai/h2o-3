import sys
import math

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# Megan Kurka found that categorical columns do not work with modelselection backward mode.  I fixed the bug and 
# extended her test to check that each time a predictor is dropped, the best performing level is compared to other 
# predictors.  If the best level is not good enough, the whole enum predictor is dropped.
def test_megan_failure():
    df = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/smalldata/demos/bank-additional-full.csv")
    y = "y"
    x = [i for i in df.col_names if i not in [y, "previous", "poutcome", "pdays"]]

    # Build & train the model:
    backward_model = H2OModelSelectionEstimator(min_predictor_number=5, seed=1234, mode="backward", 
                                                remove_collinear_columns=True)
    backward_model.train(x=x, y=y, training_frame=df)    
    coefficient_orders = backward_model._model_json['output']['coefficient_names']
    predictor_z_values = backward_model._model_json['output']['z_values']
    num_models = len(coefficient_orders)
    # verify that deleted predictors have minimum abs(z-values)
    redundantPredictors = backward_model.get_predictors_removed_per_step()[num_models-1]
    redundantPredictors = redundantPredictors[0:(len(redundantPredictors)-1)]
    redundantPredictors = [x.split("(redundant_predictor)")[0] for x in redundantPredictors]
    best_predictor_subset = backward_model.get_best_model_predictors()

    counter = 0
    for ind in list(range(num_models-1, 0, -1)):
        pred_large = coefficient_orders[ind]
        pred_small = coefficient_orders[ind-1]
        z_values_list = predictor_z_values[ind]
        predictor_removed = list(set(pred_large).symmetric_difference(pred_small))
        if len(predictor_removed) > 1:
            predictor_removed = [x for x in predictor_removed if not(x in redundantPredictors)]
        z_values_removed = extract_z_removed(pred_large, predictor_removed, z_values_list)
        
        # assert z-values removed has smallest magnitude
        x = best_predictor_subset[ind]
        assert_correct_z_removed(z_values_list, z_values_removed, pred_large, predictor_removed, x, y, df)
        
        counter += 1

def assert_correct_z_removed(z_values_backward, z_values_removed, coeff_backward, predictor_removed, x, y, df):
    glm_model = H2OGeneralizedLinearEstimator(seed=1234, remove_collinear_columns=True, lambda_=0.0, compute_p_values=True)
    glm_model.train(x=x, y=y, training_frame=df)
    cat_predictors = extractCatCols(df, x)
    num_predictors = list(set(x).symmetric_difference(cat_predictors))
    # compare both models are the same model by comparing the z-values
    model_z_values = glm_model._model_json["output"]["coefficients_table"]["z_value"]
    model_coeffs = glm_model._model_json["output"]["coefficients_table"]["names"]
    
    assert_equal_z_values(z_values_backward, coeff_backward, model_z_values, model_coeffs)
    
    num_predictor_removed = False
    for one_value in predictor_removed:
        if one_value in num_predictors:
            num_predictor_removed = True
            break
    if num_predictor_removed:
        min_z_value = min(z_values_removed)
    else:    
        min_z_value = max(z_values_removed)
        
    # check that predictor with smallest z-value magnitude is removed
    assert_correct_z_value_numerical(num_predictors, min_z_value, model_coeffs, model_z_values)
    assert_correct_z_value_categorical(cat_predictors, min_z_value, model_coeffs, model_z_values)

    for name in cat_predictors:
        for coeff_name in predictor_removed:
            if name in coeff_name: # cat predictor is removed
                x.remove(name)
                return
    x.remove(predictor_removed[0])   # numerical predictor is removed

def assert_correct_z_value_categorical(cat_predictors, min_z_value, model_coeffs, model_z_values):
    for name in cat_predictors:
        model_z = []
        for coeff_name in model_coeffs:
            if name in coeff_name:
                z_val = model_z_values[model_coeffs.index(coeff_name)]
                if math.isnan(z_val):
                    model_z.append(0)
                else:
                    model_z.append(abs(z_val))
        assert max(model_z) >= min_z_value, "predictor ({0}) with wrong z value is removed: {1} has smaller magnitude " \
                                            "than mininum_z_values {2}".format(name, model_z, min_z_value)
                
    
def assert_correct_z_value_numerical(num_predictors, min_z_value, model_coeffs, model_z_values):
    for name in num_predictors:
        pred_ind = model_coeffs.index(name)
        val = model_z_values[pred_ind]
        if not(math.isnan(val)):
            assert abs(val) >= min_z_value, "predictor with wrong z value is removed: predictor z-value: {0} has " \
                                                "smaller magnitude than minimum z_values: {1}".format(abs(val), min_z_value)    

def extractCatCols(df, x): 
    cat_pred=[]
    col_types = df.types
    for name in x:
        if col_types[name]=='enum':
            cat_pred.append(name)
    return cat_pred
    
def assert_equal_z_values(z_values_backward, coeff_backward, model_z_values, glm_coeff):
    for coeff in glm_coeff:
        backward_z_value = z_values_backward[coeff_backward.index(coeff)]
        model_z_value = model_z_values[glm_coeff.index(coeff)]
        print("for coeff: {0}, backward z-value: {1}, glm model z-value: {2}".format(coeff, backward_z_value, model_z_value))
        if (backward_z_value=='NaN'):
            assert math.isnan(model_z_value), "Expected z-value to be nan but is {0} for predictor" \
                                              " {1}".format(model_z_value, coeff)
        else:
            if math.isnan(model_z_value):
                assert False, "Expected z-value should not be nan for predictor {0}".format(coeff)
            else:
                assert abs(backward_z_value-model_z_value) < 1e-12, \
                    "Expected z-value: {0}.  Actual z_value: {1}. They are very different." \
                    "".format(backward_z_value, model_z_value)

 
def extract_z_removed(pred_large, predictor_removed, z_values_large):
    z_values_removed = []
    for x in predictor_removed:
        z_value = z_values_large[pred_large.index(x)]
        if z_value=='NaN':
            z_values_removed.append(0)
        else:
            z_values_removed.append(abs(z_value))
    return z_values_removed
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_megan_failure)
else:
    test_megan_failure()
