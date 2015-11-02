import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils

def deeplearning_basic():

  from h2o.estimators.deeplearning import H2ODeepLearningEstimator

  iris_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
  hh = H2ODeepLearningEstimator(loss="CrossEntropy")
  hh.train(x=range(3), y=4, training_frame=iris_hex)
  hh.show()

if __name__ == "__main__":
  pyunit_utils.standalone_test(deeplearning_basic)
else:
  deeplearning_basic()
