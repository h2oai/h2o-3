from builtins import range
import sys, os
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.deeplearning import H2ODeepLearningEstimator

def deeplearning_no_hidden():
  iris_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

  hh = H2ODeepLearningEstimator(hidden=[], loss="CrossEntropy", export_weights_and_biases=True)
  hh.train(x=list(range(4)), y=4, training_frame=iris_hex)
  hh.show()
  weights1 = hh.weights(0)
  assert weights1.shape[0] == 3
  assert weights1.shape[1] == 4

if __name__ == "__main__":
  pyunit_utils.standalone_test(deeplearning_no_hidden)
else:
  deeplearning_no_hidden()
