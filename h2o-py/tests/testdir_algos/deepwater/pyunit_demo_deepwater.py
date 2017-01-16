from __future__ import print_function
from builtins import range
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
import h2o, tests
from h2o.estimators.deepwater import H2ODeepWaterEstimator


def deepwater_demo():
  if not H2ODeepWaterEstimator.available(): return

  # Training data
  train_data = h2o.import_file(path=tests.locate("smalldata/gbm_test/ecology_model.csv"))
  train_data = train_data.drop('Site')
  train_data['Angaus'] = train_data['Angaus'].asfactor()
  print(train_data.describe())
  train_data.head()

  # Testing data
  test_data = h2o.import_file(path=tests.locate("smalldata/gbm_test/ecology_eval.csv"))
  test_data['Angaus'] = test_data['Angaus'].asfactor()
  print(test_data.describe())
  test_data.head()

  # Run DeepWater (ideally, use a GPU - this would be slow on CPUs)

  dl = H2ODeepWaterEstimator(epochs=50, hidden=[4096,4096,4096], hidden_dropout_ratios=[0.2,0.2,0.2])
  dl.train(x=list(range(1,train_data.ncol)),
           y="Angaus",
           training_frame=train_data,
           validation_frame=test_data)
  dl.show()

if __name__ == "__main__":
  pyunit_utils.standalone_test(deepwater_demo)
else:
  deepwater_demo()
