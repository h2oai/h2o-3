from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

# test to find out why maxr is slow
def test_maxr_slow():
    train = pyunit_utils.random_dataset_real_only(14200, 189, misFrac=0.0, randSeed=1234)
  #  train = h2o.create_frame(rows=142000, cols=189, seed=12345)

    predictors = train.columns
    response = predictors[0]
    predictors.remove(response)

    maxrModel = H2OModelSelectionEstimator(mode="maxr", max_predictor_number=10, intercept=True)
    maxrModel.train(x=predictors, y=response, training_frame=train)
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxr_slow)
else:
    test_maxr_slow()
