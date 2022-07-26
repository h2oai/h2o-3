import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

def test_maxrsweep_replacement():
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
    maxrsweepSmall_model = H2OModelSelectionEstimator(mode="maxrsweepsmall", max_predictor_number=npred, intercept=True)
    maxrsweepSmall_model.train(x=predictors, y=response, training_frame=train)
    bestPredictorSubsets = maxrsweepSmall_model.get_best_model_predictors()
    maxrsweepfull_model = H2OModelSelectionEstimator(mode="maxrsweepfull", max_predictor_number=npred, intercept=True)
    maxrsweepfull_model.train(x=predictors, y=response, training_frame=train)
    bestPredictorSubsetsfull = maxrsweepfull_model.get_best_model_predictors()
    maxrsweephybrid_model = H2OModelSelectionEstimator(mode="maxrsweephybrid", max_predictor_number=npred, intercept=True)
    maxrsweephybrid_model.train(x=predictors, y=response, training_frame=train)
    bestPredictorSubsetshybrid = maxrsweephybrid_model.get_best_model_predictors()
    for index in range(len(bestPredictorSubsets)):
        correctOneSubset = correctPredSubsets[index]
        modelOneSubset = bestPredictorSubsets[index]
        modelFullOneSubet = bestPredictorSubsetsfull[index]
        modelHybridOneSubet = bestPredictorSubsetshybrid[index]
        assert correctOneSubset.sort()==modelOneSubset.sort(), \
            "Expected predictor subset: {0}, actual predictor subset from maxrsweepsmall: {1}.  " \
            "They are different".format(correctOneSubset, modelOneSubset)
        assert correctOneSubset.sort()==modelFullOneSubet.sort(), \
            "Expected predictor subset: {0}, actual predictor subset from maxrsweepfull: {1}.  " \
            "They are different".format(correctOneSubset, modelFullOneSubet)
        assert correctOneSubset.sort()==modelHybridOneSubet.sort(), \
            "Expected predictor subset: {0}, actual predictor subset from maxrsweephybrid: {1}.  " \
            "They are different".format(correctOneSubset, modelHybridOneSubet)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrsweep_replacement)
else:
    test_maxrsweep_replacement()
