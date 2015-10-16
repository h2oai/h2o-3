import sys
sys.path.insert(1, "../../../")
import h2o, tests

def weights_and_distributions():

  htable  = h2o.upload_file(tests.locate("smalldata/gbm_test/moppe.csv"))
  htable["premiekl"] = htable["premiekl"].asfactor()
  htable["moptva"] = htable["moptva"].asfactor()
  htable["zon"] = htable["zon"]

  from h2o.estimators.deeplearning import H2ODeepLearningEstimator
  # gamma
  dl = H2ODeepLearningEstimator(distribution="gamma")
  dl.train(X=range(3),y="medskad",training_frame=htable, weights_column="antskad")
  predictions = dl.predict(htable)

  # gaussian
  dl = H2ODeepLearningEstimator(distribution="gaussian")
  dl.train(X=range(3),y="medskad",training_frame=htable, weights_column="antskad")
  predictions = dl.predict(htable)

  # poisson
  dl = H2ODeepLearningEstimator(distribution="poisson")
  dl.train(X=range(3),y="medskad",training_frame=htable, weights_column="antskad")
  predictions = dl.predict(htable)

  # tweedie
  dl = H2ODeepLearningEstimator(distribution="tweedie")
  dl.train(X=range(3),y="medskad",training_frame=htable, weights_column="antskad")
  predictions = dl.predict(htable)

if __name__ == "__main__":
  tests.run_test(sys.argv, weights_and_distributions)
