import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils


def checkpoint_new_category_in_response():

  sv = h2o.upload_file(pyunit_utils.locate("smalldata/iris/setosa_versicolor.csv"))
  iris = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris.csv"))

  from h2o.estimators.gbm import H2OGradientBoostingEstimator
  m1 = H2OGradientBoostingEstimator(ntrees=100)
  m1.train(x=[0,1,2,3],y=4, training_frame=sv)

  # attempt to continue building model, but with an expanded categorical response domain.
  # this should fail
  try:
    m2 = H2OGradientBoostingEstimator(ntrees=200, checkpoint=m1.model_id)
    m2.train(x=[0,1,2,3],y=4,training_frame=iris)
    assert False, "Expected continued model-building to fail with new categories introduced in response"
  except EnvironmentError:
    pass


if __name__ == "__main__":
  pyunit_utils.standalone_test(checkpoint_new_category_in_response)
else:
  checkpoint_new_category_in_response()
