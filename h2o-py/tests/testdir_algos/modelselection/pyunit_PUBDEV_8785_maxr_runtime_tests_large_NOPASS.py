import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

# This file is used mainly for run time profiles and no need to be tested on Jenkins
def test_maxrsweep_replacement():
    allPreds = a = list(range(10, 210, 10))
    for npred in allPreds:
        h2o.remove_all()
        train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/gaussian_500Cols_500KRows.csv"))
        response="response"
        predictors = train.names
        predictors.remove(response)
        maxrsweep_model = H2OModelSelectionEstimator(mode="maxrsweepsmall", max_predictor_number=npred, intercept=True)
        maxrsweep_model.train(x=predictors, y=response, training_frame=train)
        print("Maxrsweepsmall Run time for npred {1} (ms): {0}".format(maxrsweep_model._model_json["output"]["run_time"], npred))
        h2o.remove_all()
        train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/gaussian_500Cols_500KRows.csv"))
        response="response"
        predictors = train.names
        predictors.remove(response)        
        maxrsweep2_model = H2OModelSelectionEstimator(mode="maxrsweepfull", max_predictor_number=npred, intercept=True)
        maxrsweep2_model.train(x=predictors, y=response, training_frame=train)
        print("Maxrsweepfull Run time for npred {1} (ms): {0}".format(maxrsweep2_model._model_json["output"]["run_time"], npred))
        h2o.remove_all()
        train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/gaussian_500Cols_500KRows.csv"))
        response="response"
        predictors = train.names
        predictors.remove(response)
        maxrsweep3_model = H2OModelSelectionEstimator(mode="maxrsweephybrid", max_predictor_number=npred, intercept=True)
        maxrsweep3_model.train(x=predictors, y=response, training_frame=train)
        print("Maxrsweephybrid Run time for npred {1} (ms): {0}".format(maxrsweep3_model._model_json["output"]["run_time"], npred))
        h2o.remove_all()
        train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/gaussian_500Cols_500KRows.csv"))
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
