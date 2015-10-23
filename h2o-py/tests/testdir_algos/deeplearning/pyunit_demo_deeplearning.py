import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
import h2o, tests


def deeplearning_demo():
  # Training data
  train_data = h2o.import_file(path=tests.locate("smalldata/gbm_test/ecology_model.csv"))
  train_data = train_data.drop('Site')
  train_data['Angaus'] = train_data['Angaus'].asfactor()
  print train_data.describe()
  train_data.head()

  # Testing data
  test_data = h2o.import_file(path=tests.locate("smalldata/gbm_test/ecology_eval.csv"))
  test_data['Angaus'] = test_data['Angaus'].asfactor()
  print test_data.describe()
  test_data.head()

  # Run DeepLearning
  from h2o.estimators.deeplearning import H2ODeepLearningEstimator
  dl = H2ODeepLearningEstimator(loss="CrossEntropy", epochs=1000, hidden=[20,20,20])
  dl.train(x=range(1,train_data.ncol),
           y="Angaus",
           training_frame=train_data,
           validation_frame=test_data)
  dl.show()

if __name__ == "__main__":
  pyunit_utils.standalone_test(deeplearning_demo)
else:
  deeplearning_demo()
