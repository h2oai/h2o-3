import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

def test_maxrsweep_NPE():
    train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv")
    response="response"
    predictors = train.names
    predictors.remove(response)
    npred = 100
    maxrsweep_model = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True)
    maxrsweep_model.train(x=predictors, y=response, training_frame=train)
    bestPredictorSubsets = maxrsweep_model.get_best_model_predictors()
    assert len(bestPredictorSubsets) == npred, "Expected number of predictors: {0}, Actual: " \
                                               "{1}".format(npred, len(bestPredictorSubsets))


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrsweep_NPE)
else:
    test_maxrsweep_NPE()
