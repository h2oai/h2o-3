import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

# This file is used mainly for run time profiles and no need to be tested on Jenkins
def test_maxrsweep_replacement():
    allPreds = a = list(range(10, 30, 10))
    for npred in allPreds:
        h2o.remove_all()
        train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv"))
        response="response"
        predictors = train.names
        predictors.remove(response)
        maxrsweep3_model = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True, build_glm_model=False)
        maxrsweep3_model.train(x=predictors, y=response, training_frame=train)
        print("Maxrsweep Run time for npred {1} (ms): {0}".format(maxrsweep3_model._model_json["output"]["run_time"], npred))

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrsweep_replacement)
else:
    test_maxrsweep_replacement()
