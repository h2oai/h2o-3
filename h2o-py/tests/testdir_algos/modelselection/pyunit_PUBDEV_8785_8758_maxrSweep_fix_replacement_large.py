import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

def test_maxrsweep_replacement():
    correct_pred_subsets = [["C78",'Intercept'], ["C78","C97",'Intercept'], ["C78","C97","C75",'Intercept'], 
                          ["C78","C97","C75","C76",'Intercept'], ["C78","C97","C75","C76","C88",'Intercept'], 
                          ["C78","C97","C75","C76","C88","C89",'Intercept'], 
                          ["C78","C97","C75","C76","C88","C89","C101",'Intercept'], 
                          ["C78","C97","C75","C76","C88","C89","C7","C86",'Intercept'],
                          ["C78","C97","C75","C76","C88","C89","C7","C86","C101",'Intercept'],
                          ["C78","C97","C75","C76","C88","C89","C7","C86","C101","C4",'Intercept']]
    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv"))
    response="response"
    predictors = train.names
    predictors.remove(response)
    npred = 10
    maxrsweep_model = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True, standardize=True)
    maxrsweep_model.train(x=predictors, y=response, training_frame=train)
    maxrsweep_best_predictors = maxrsweep_model.coef()
    maxrsweep_model_MM = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True, 
                                                    standardize=True, multinode_mode=True)
    maxrsweep_model_MM.train(x=predictors, y=response, training_frame=train)
    maxrsweep_best_predictors_MM = maxrsweep_model_MM.coef()
    maxr_model = H2OModelSelectionEstimator(mode="maxr", max_predictor_number=npred, intercept=True)
    maxr_model.train(x=predictors, y=response, training_frame=train)
    maxr_best_predictors = maxr_model.coef()

    for index in range(npred):
        correct_pred_subsets[index].sort()
        maxr_one_coef = list(maxr_best_predictors[index].keys())
        maxr_one_coef.sort()
        maxrsweep_one_coef = list(maxrsweep_best_predictors[index].keys())
        maxrsweep_one_coef.sort()
        maxrsweep_one_coef_MM = list(maxrsweep_best_predictors_MM[index].keys())
        maxrsweep_one_coef_MM.sort()
        assert correct_pred_subsets[index]==maxr_one_coef, "Expected predictor subset: {0}, actual predictor subset from" \
                                                    " maxr: {1}.  They are different".format(correct_pred_subsets[index], maxr_one_coef)
        assert correct_pred_subsets[index]==maxrsweep_one_coef, "Expected predictor subset: {0}, actual predictor subset from" \
                                                    " maxrsweep: {1}.  They are different".format(correct_pred_subsets[index], maxrsweep_one_coef)
        assert correct_pred_subsets[index]==maxrsweep_one_coef_MM, "Expected predictor subset: {0}, actual predictor subset from" \
                                                                " maxrsweep with multinode_mode: {1}.  They are " \
                                                                   "different".format(correct_pred_subsets[index], maxrsweep_one_coef_MM)
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrsweep_replacement)
else:
    test_maxrsweep_replacement()
