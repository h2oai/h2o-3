from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test modelselection with mode=gaussian for regression only.  In particular, we are interested in
# making sure the models returned will agree with customer run result and it did!
def test_modelselection_backward_gaussian():
    predictor_elimination_order = ["C72", "C70", "C69", "C48", "C38", "C96", "C10", "C29", "C22", "C100", "C82", "C56", 
                                   "C92", "C99", "C57"]
    eliminated_p_values = [0.9822, 0.9054, 0.7433, 0.4095, 0.1679, 0.1551, 0.0438, 0.0119, 0.0107, 0.0094, 0.0099, 
                           0.0066, 0.0003, 0.0002, 0.0002]
    d = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/model_selection/maxrglm100Cols50KRowsWeighted.csv"))
    my_y = "response"
    my_x = d.names
    my_x.remove(my_y)
    my_x.remove("weight")
    min_predictor_num = 100-len(predictor_elimination_order)
    model_backward = modelSelection(seed=12345, min_predictor_number=min_predictor_num, mode="backward", family='gaussian',
                                weights_column='weight')
    model_backward.train(training_frame=d, x=my_x, y=my_y)
    # check predictor deletion order same as in predictor_elimination_order
    predictor_orders = model_backward._model_json['output']['best_model_predictors']
    num_models = len(predictor_orders)
    counter = 0
    for ind in list(range(num_models-1, 0, -1)):
        pred_large = model_backward._model_json["output"]["best_model_predictors"][ind]
        pred_small = model_backward._model_json["output"]["best_model_predictors"][ind-1]
        predictor_removed = set(pred_large).symmetric_difference(pred_small).pop()
        assert predictor_removed==predictor_elimination_order[counter], "expected eliminated predictor {0}, " \
                                                                        "actual eliminated predictor {1}".format(predictor_elimination_order[counter], predictor_removed)
        
        predictor_removed_index = model_backward._model_json["output"]["coefficient_names"][ind].index(predictor_removed)
        removed_pvalue = round(model_backward._model_json["output"]["coef_p_values"][ind][predictor_removed_index], 4)
        # assert p-values of coefficients removed by h2o equals to customer ones
        assert abs(removed_pvalue-eliminated_p_values[counter]) < 1e-6, \
            "Expected p-value of eliminated coefficient: {0}. Actual: {1}. They are very different." \
            "".format(eliminated_p_values[counter], removed_pvalue)
        counter += 1
        coefs = model_backward.coef(len(pred_large)) # check coefficients result correct length
        assert len(coefs) == len(pred_large)+1, "Expected coef length: {0}, Actual: {1}".format(len(coefs), len(pred_large)+1)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_backward_gaussian)
else:
    test_modelselection_backward_gaussian()
