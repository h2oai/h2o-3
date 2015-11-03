from h2o.estimators.estimator_base import H2OEstimator
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import os


def save_load_model():

  training_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/benign.csv"))

  Y = 3
  X = range(3) + range(4,11)

  from h2o.estimators.glm import H2OGeneralizedLinearEstimator
  model = H2OGeneralizedLinearEstimator(model_id="original_id", family="binomial", alpha=0, Lambda=1e-5)
  model.train(x=X,y=Y, training_frame=training_data)
  assert model.model_id == "original_id"
  model.model_id = "new_id"
  assert model.model_id == "new_id"



if __name__ == "__main__":
  pyunit_utils.standalone_test(save_load_model)
else:
  save_load_model()
