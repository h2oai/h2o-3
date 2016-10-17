from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deepwater import H2ODeepWaterEstimator

def deepwater_regression():
  if not H2ODeepWaterEstimator.available(): return

  frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))
  frame[1] = frame[1].asnumeric()
  print(frame.head(5))
  model = H2ODeepWaterEstimator(epochs=50, learning_rate=1e-3)
  model.train(x=[0],y=1, training_frame=frame)
  model.show()
  error = model.model_performance(train=True).mae()
  assert error < 0.3, "mean absolute error is too high : " + str(error)

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_regression)
else:
  deepwater_regression()
