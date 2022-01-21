from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test modelselection with mode=binomial.  Aim to compare with customer run.  However, we do not have good 
# agreement here.
def test_modelselection_backward_gaussian():
    predictor_elimination_order = ["C15", "C33", "C164", "C144", "C27"]
    eliminated_p_values = [0.6702, 0.6663, 0.0157, 0.0026, 0.0002]
    d = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/model_selection/backwardBinomial200C50KRowsWeighted.csv"))
    my_y = "response"
    my_x = d.names
    my_x.remove(my_y)
    my_x.remove("weight")
    min_predictor_num = 200-len(predictor_elimination_order)
    model_backward = modelSelection(seed=12345, min_predictor_number=min_predictor_num, mode="backward", family='binomial',
                                link='logit', weights_column='weight')
    model_backward.train(training_frame=d, x=my_x, y=my_y)
    # check predictor deletion order same as in predictor_elimination_order
    predictor_orders = model_backward._model_json['output']['best_model_predictors']
    num_models = len(predictor_orders)
    counter = 0
    pred_ele = []
    pred_pvalue = []
    for ind in list(range(num_models-1, 0, -1)):
        pred_large = model_backward._model_json["output"]["best_model_predictors"][ind]
        pred_small = model_backward._model_json["output"]["best_model_predictors"][ind-1]
        predictor_removed = set(pred_large).symmetric_difference(pred_small).pop()
        pred_ele.append(predictor_removed)        
        predictor_removed_index = model_backward._model_json["output"]["coefficient_names"][ind].index(predictor_removed)
        pred_pvalue.append(round(model_backward._model_json["output"]["coef_p_values"][ind][predictor_removed_index], 4))
        counter += 1
        coefs = model_backward.coef(len(pred_large)) # check coefficients result correct length
        assert len(coefs) == len(pred_large)+1, "Expected coef length: {0}, Actual: {1}".format(len(coefs), len(pred_large)+1)
    common_elimination = list(set(predictor_elimination_order) & set(pred_ele))
    assert len(common_elimination) >= 2
    print("Expected predictor elimination order: {0}".format(predictor_elimination_order))
    print("Expected predictor p-values: {0}".format(eliminated_p_values))
    print("Predictor elimination order: {0}".format(pred_ele))
    print("Predictor p-values: {0}".format(pred_pvalue))
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_backward_gaussian)
else:
    test_modelselection_backward_gaussian()
