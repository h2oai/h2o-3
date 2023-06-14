import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test modelselection with mode=binomial.  Aim to compare with customer run.  However, we do not have good 
# agreement here.
def test_modelselection_backward_gaussian():
    predictor_elimination_order = ['C33', 'C24', 'C164', 'C66', 'C15']
    eliminated_p_values = [0.9711, 0.0694, 0.0388, 0.0127, 0.0009]
    tst_data = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/backwardBinomial200C50KRows.csv"))
    predictors = tst_data.columns[0:-1]
    response_col = 'response'
    weight = 'wt'
    tst_data['wt']=1
    tst_data[tst_data['response']==1,'wt'] = 100
    tst_data['response']=tst_data['response'].asfactor()
    
    min_predictor_num = 200-len(predictor_elimination_order)
    model_backward = modelSelection(family = 'binomial', weights_column = weight, mode='backward', 
                                    min_predictor_number=min_predictor_num)
    model_backward.train(training_frame=tst_data, x=predictors, y=response_col)
    # check predictor deletion order same as in predictor_elimination_order
    predictor_orders = model_backward._model_json['output']["best_predictors_subset"]
    num_models = len(predictor_orders)
    counter = 0
    pred_ele = []
    pred_pvalue = []
    for ind in list(range(num_models-1, 0, -1)):
        pred_large = model_backward._model_json["output"]["best_predictors_subset"][ind]
        pred_small = model_backward._model_json["output"]["best_predictors_subset"][ind-1]
        predictor_removed = set(pred_large).symmetric_difference(pred_small).pop()
        pred_ele.append(predictor_removed)        
        predictor_removed_index = model_backward._model_json["output"]["coefficient_names"][ind].index(predictor_removed)
        pred_pvalue.append(round(model_backward._model_json["output"]["coef_p_values"][ind][predictor_removed_index], 4))
        counter += 1
        coefs = model_backward.coef(len(pred_large)) # check coefficients result correct length
        assert len(coefs) == len(pred_large), "Expected coef length: {0}, Actual: {1}".format(len(coefs), len(pred_large))
    assert pred_ele == predictor_elimination_order, "Expected predictor elimination order: {0}.  Actual: " \
                                                    "{1}".format(predictor_elimination_order, pred_ele)
    pyunit_utils.equal_two_arrays(pred_pvalue, eliminated_p_values, tolerance=1e-6)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_modelselection_backward_gaussian)
else:
    test_modelselection_backward_gaussian()
