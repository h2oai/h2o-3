from __future__ import print_function
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.deepwater import H2ODeepWaterEstimator

def deepwater_lenet():
  if not H2ODeepWaterEstimator.available(): return

  frame = h2o.import_file(pyunit_utils.locate("bigdata/laptop/deepwater/imagenet/cat_dog_mouse.csv"))
  print(frame.head(5))
  model = H2ODeepWaterEstimator(epochs=100, learning_rate=1e-3, network='lenet', score_interval=0, train_samples_per_iteration=1000)
  model.train(x=[0],y=1, training_frame=frame)

  extracted = model.deepfeatures(frame, "pooling1_output")
  #print(extracted.describe())
  print(extracted.ncols)
  assert extracted.ncols == 800, "extracted frame doesn't have 800 columns"

  extracted = model.deepfeatures(frame, "activation2_output")
  #print(extracted.describe())
  print(extracted.ncols)
  assert extracted.ncols == 500, "extracted frame doesn't have 500 columns"

  h2o.remove_all()

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_lenet)
else:
  deepwater_lenet()
