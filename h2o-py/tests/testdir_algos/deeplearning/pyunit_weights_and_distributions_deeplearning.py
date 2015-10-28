import sys, os
sys.path.insert(1, os.path.join("..",".."))
import h2o
from tests import pyunit_utils

def weights_and_distributions():

  htable  = h2o.upload_file(pyunit_utils.locate("smalldata/gbm_test/moppe.csv"))
  htable["premiekl"] = htable["premiekl"].asfactor()
  htable["moptva"] = htable["moptva"].asfactor()
  htable["zon"] = htable["zon"]

  from h2o.estimators.deeplearning import H2ODeepLearningEstimator
  # gamma
  dl = H2ODeepLearningEstimator(distribution="gamma")
  dl.train(x=range(3),y="medskad",training_frame=htable, weights_column="antskad")
  predictions = dl.predict(htable)

  # gaussian
  dl = H2ODeepLearningEstimator(distribution="gaussian")
  dl.train(x=range(3),y="medskad",training_frame=htable, weights_column="antskad")
  predictions = dl.predict(htable)

  # poisson
  dl = H2ODeepLearningEstimator(distribution="poisson")
  dl.train(x=range(3),y="medskad",training_frame=htable, weights_column="antskad")
  predictions = dl.predict(htable)

  # tweedie
  dl = H2ODeepLearningEstimator(distribution="tweedie")
  dl.train(x=range(3),y="medskad",training_frame=htable, weights_column="antskad")
  predictions = dl.predict(htable)

if __name__ == "__main__":
  pyunit_utils.standalone_test(weights_and_distributions)
else:
  weights_and_distributions()