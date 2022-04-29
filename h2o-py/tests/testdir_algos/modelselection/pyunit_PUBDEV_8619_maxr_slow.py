from __future__ import print_function
from __future__ import division
import sys
sys.path.insert(1, “../../../“)
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator
# test to find out why maxr is slow
def test_maxr_slow():
  npred = 10
  hf = h2o.import_file(pyunit_utils.locate("smalldata/model_selection/maxrglm200Cols50KRows.csv"))
  hf = hf.cbind(hf2)
  hf = hf.cbind(hf3)
  target = ‘response’
  predictors = hf.names
  predictors.remove(target)
  maxrModel = H2OModelSelectionEstimator(mode=“maxr”, max_predictor_number=npred, intercept=True)
  maxrModel.train(x=predictors, y=target, training_frame=hf)
  print(“Time elapsed : {0}“.format(maxrModel._model_json[“output”][“run_time”]))
  resultRun = maxrModel.result()
  assert resultRun.nrow==npred
if __name__ == “__main__“:
  pyunit_utils.standalone_test(test_maxr_slow)
else:
  test_maxr_slow()
