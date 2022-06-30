import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

# compare mode maxr and maxrsweep.  They should generate the same models.
def test_maxr_slow():
    ncol = 189
    train = h2o.create_frame("testFrame", 14200, ncol, factors=10, seed=1234)
    predictors = train.columns
    npred = 4
    for index in range(ncol):
        if train.type(index)=='real':
            response = predictors[index]
            break
    predictors.remove(response)
    maxr_model = H2OModelSelectionEstimator(mode="maxr", max_predictor_number=npred, intercept=True)
    maxr_model.train(x=predictors, y=response, training_frame=train)
    t1 = maxr_model._model_json["output"]["run_time"]
    print("maxr mode run time {0}".format(t1))
    result_maxr = maxr_model.result()
    maxrsweep_model = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True)
    maxrsweep_model.train(x=predictors, y=response, training_frame=train)
    t2 = maxrsweep_model._model_json["output"]["run_time"]
    print("maxrsweep mode run time {0}".format(t2))
    result_maxrsweep = maxrsweep_model.result()

    assert t1 >= t2, "mode maxrsweep run time {1} should be much shorter than model maxr run time {2} but is not. "
    pyunit_utils.compare_frames_local(result_maxr[2], result_maxrsweep[2], prob=1.0, tol=1e-6)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxr_slow)
else:
    test_maxr_slow()
