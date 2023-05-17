import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator as modelSelection

# test modelselection algorithm for regression only.  Make sure the result frame contains the correct information 
# regarding added and deleted predictors
def test_gaussian_result_frame_model_id():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    my_y = "GLEASON"
    my_x = ["AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    
    maxr_model = modelSelection(seed=12345, max_predictor_number=7, mode="maxr")
    maxr_model.train(training_frame=d, x=my_x, y=my_y)
    verifyCorrectAddedRemovedPreds(maxr_model, 'maxr')
        
    maxrsweep_model = modelSelection(seed=12345, max_predictor_number=7, mode="maxrsweep", build_glm_model=True)
    maxrsweep_model.train(training_frame=d, x=my_x, y=my_y)
    verifyCorrectAddedRemovedPreds(maxrsweep_model, 'maxrsweep')

    maxrsweep_model_glm = modelSelection(seed=12345, max_predictor_number=7, mode="maxrsweep")
    maxrsweep_model_glm.train(training_frame=d, x=my_x, y=my_y)
    verifyCorrectAddedRemovedPreds(maxrsweep_model_glm, 'maxrsweep')

    maxrsweep_model_MM = modelSelection(seed=12345, max_predictor_number=7, mode="maxrsweep", multinode_mode=True)
    maxrsweep_model_MM.train(training_frame=d, x=my_x, y=my_y)
    verifyCorrectAddedRemovedPreds(maxrsweep_model_MM, 'maxrsweep')

    allsubsets_model = modelSelection(seed=12345, max_predictor_number=7, mode="allsubsets")
    allsubsets_model.train(training_frame=d, x=my_x, y=my_y)
    verifyCorrectAddedRemovedPreds(allsubsets_model, 'allsubsets')
        
    backward_model = modelSelection(seed=12345, min_predictor_number=3, mode="backward")
    backward_model.train(training_frame=d, x=my_x, y=my_y)
    verifyCorrectAddedRemovedPreds(backward_model, 'backward')

def verifyCorrectAddedRemovedPreds(model, mode):
    resultFrame = model.result()
    best_predictors = model.get_best_model_predictors()
    removed_predictors = model.get_predictors_removed_per_step()
    
    # assert result frame and model summary results are the same
    compareFrameNTuple(resultFrame[3], best_predictors)
    compareFrameNTuple(resultFrame[4], removed_predictors)
    if not(mode=='backward'):
        added_predictors = model.get_predictors_added_per_step()
        compareFrameNTuple(resultFrame[5], added_predictors)
    else:
        added_predictors = None

    # verify added and removed predictors are correctly derived
    assertCorrectAddedDeletedPreds(best_predictors, added_predictors, removed_predictors, mode)

def assertCorrectAddedDeletedPreds(best_predictors, added_predictors, removed_predictors, mode):
    numRow = len(best_predictors)
    for index in range(numRow):
        if not(mode=='backward'): # assert predictors are added correctly
            add_pred = added_predictors[index]
            if index==0:
                for indexB in range(len(add_pred)):
                    assert add_pred[indexB] in best_predictors[index], \
                        "Current model predictors: {0}, predictor: {1} is added " \
                        "incorrectly".format(best_predictors[index], add_pred[indexB])
            else:
                for indexB in range(len(add_pred)):
                    assert not(add_pred[indexB] in best_predictors[index-1]) and (add_pred[indexB] in best_predictors[index]), \
                        "Smaller model predictors: {0}, current model predictors: {1}, predictor: {2} is added " \
                        "incorrectly.".format(best_predictors[index-1], best_predictors[index], add_pred[indexB])

        del_pred = removed_predictors[index]
        if index > 0:
            for indexB in range(len(del_pred)):
                if not(del_pred[indexB]==''):
                    if mode == 'backward':
                        assert (del_pred[indexB] in best_predictors[index]) and not(del_pred[indexB] in best_predictors[index-1]), \
                        "Smaller model predictors: {0}, current model predictors: {1}, predictor: {2} is removed " \
                        "incorrectly.".format(best_predictors[index-1], best_predictors[index], del_pred[indexB])
                    else:
                        assert (del_pred[indexB] in best_predictors[index-1]) and not(del_pred[indexB] in best_predictors[index]), \
                            "Smaller model predictors: {0}, current model predictors: {1}, predictor: {2} is removed " \
                            "incorrectly.".format(best_predictors[index-1], best_predictors[index], del_pred[indexB])
                                                                                           
def compareFrameNTuple(oneCol, oneList):
    temp = oneCol.as_data_frame(use_pandas=False)
    numRow = oneCol.nrows
    for index in range(numRow):
        assert temp[index+1].sort() == oneList[index].sort(), "Expected: {0}, Actual: {1}.  They are " \
                                                              "different".format(temp[index], oneList[index])

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gaussian_result_frame_model_id)
else:
    test_gaussian_result_frame_model_id()
