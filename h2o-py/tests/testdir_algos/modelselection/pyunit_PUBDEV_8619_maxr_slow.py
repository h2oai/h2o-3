from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator
import time
# test to find out why maxr is slow
def test_maxr_slow():
    ncol = 189
    train = h2o.create_frame("testFrame", 14200, ncol, seed=1234)
    predictors = train.columns
    for index in range(ncol):
        if train.type(index)=='real':
            response = predictors[index]
            break
    predictors.remove(response)
    start = time.time()
    maxrModel = H2OModelSelectionEstimator(mode="maxr", max_predictor_number=10, intercept=True)
    maxrModel.train(x=predictors, y=response, training_frame=train)
    print("Time elapsed with nparallelism=1: {0}".format(time.time() - start))
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxr_slow)
else:
    test_maxr_slow()
