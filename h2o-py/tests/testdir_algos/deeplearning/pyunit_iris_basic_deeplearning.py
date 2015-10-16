import sys, os
sys.path.insert(1,"../../../")
import h2o, tests

def deeplearning_basic():

  from h2o.estimators.deeplearning import H2ODeepLearningEstimator

  iris_hex = h2o.import_file(path=tests.locate("smalldata/iris/iris.csv"))
  hh = H2ODeepLearningEstimator(loss="CrossEntropy")
  hh.train(X=range(3), y=4, training_frame=iris_hex)
  hh.show()

if __name__ == '__main__':
  tests.run_test(sys.argv, deeplearning_basic)
