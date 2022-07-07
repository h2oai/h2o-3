import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

# compare mode maxr and maxrsweep.  They should generate the same models.
def test_maxr_slow():
    correctPredSubsets = [["C78",'Intercept'], ["C78","C97",'Intercept'], ["C78","C97","C75",'Intercept'], 
                          ["C78","C97","C75","C76",'Intercept'], ["C78","C97","C75","C76","C88",'Intercept'], 
                          ["C78","C97","C75","C76","C88","C89",'Intercept'], 
                          ["C78","C97","C75","C76","C88","C89","C101",'Intercept'], 
                          ["C78","C97","C75","C76","C88","C89","C7","C86",'Intercept'],
                          ["C78","C97","C75","C76","C88","C89","C7","C86","C101",'Intercept'],
                          ["C78","C97","C75","C76","C88","C89","C7","C86","C101","C4"'Intercept']]
    #train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv")
    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv"))
    response="response"
    predictors = train.names
    predictors.remove(response)
    npred = 10
    maxrsweep_model = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True)
    maxrsweep_model.train(x=predictors, y=response, training_frame=train)
    bestPredictorSubsets = maxrsweep_model.get_best_model_predictors()
    for index in range(len(bestPredictorSubsets)):
        correctOneSubset = correctPredSubsets[index]
        modelOneSubset = bestPredictorSubsets[index]
        assert correctOneSubset.sort()==modelOneSubset.sort(), \
            "Expected predictor subset: {0}, actual predictor subset: {1}.  " \
            "They are different".format(correctOneSubset, modelOneSubset)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxr_slow)
else:
    test_maxr_slow()
