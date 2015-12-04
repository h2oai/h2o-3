from tests import pyunit_utils
import sys
sys.path.insert(1, "../../../")
import h2o

def distribution_behaviorGBM():



  #Log.info("==============================")
  #Log.info("Default Behavior - Gaussian")
  #Log.info("==============================")
  eco = h2o.import_frame(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))

  # 0/1 response: expect gaussian
  eco_model = h2o.gbm(x=eco[2:13], y=eco["Angaus"])
  #  assert isinstance(eco_model,h2o.model.regression.H2ORegressionModel) #REST API 3 output
  #  assert isinstance(eco_model,h2o.model.estimator_base.H2OEstimator)  # Tib5 output
  assert(h2o.get_model(eco_model.model_id)._metrics_class,'h2o.model.metrics_base.H2ORegressionsModelMetrics')
  # more than 2 integers for response: expect gaussian
  cars = h2o.import_frame(path=pyunit_utils.locate("smalldata/junit/cars.csv"))

  cars_model = h2o.gbm(x=cars[3:7], y=cars["cylinders"])
  #  assert isinstance(cars_model,h2o.model.regression.H2ORegressionModel)
  #  assert isinstance(cars_model,h2o.model.estimator_base.H2OEstimator)
  assert(h2o.get_model(cars_model.model_id)._metrics_class,'h2o.model.metrics_base.H2ORegressionsModelMetrics')

  #Log.info("==============================")
  #Log.info("Gaussian Behavior")
  #Log.info("==============================")
  # 0/1 response: expect gaussian
  eco_model = h2o.gbm(x=eco[2:13], y=eco["Angaus"], distribution="gaussian")
  #  assert isinstance(eco_model,h2o.model.regression.H2ORegressionModel)
  #  assert isinstance(eco_model,h2o.model.estimator_base.H2OEstimator)
  assert(h2o.get_model(eco_model.model_id)._metrics_class,'h2o.model.metrics_base.H2ORegressionsModelMetrics')
  # character response: expect error
  try:
    eco_model = h2o.gbm(x=eco[1:8], y=eco["Method"], distribution="gaussian")
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  #Log.info("==============================")
  #Log.info("Bernoulli Behavior")
  #Log.info("==============================")
  # 0/1 response: expect bernoulli
  eco_model = h2o.gbm(x=eco[2:13], y=eco["Angaus"].asfactor(), distribution="bernoulli")
  #  assert isinstance(eco_model,h2o.model.binomial.H2OBinomialModel)
  #  assert isinstance(eco_model,h2o.model.estimator_base.H2OEstimator)
  assert(h2o.get_model(eco_model.model_id)._metrics_class,'h2o.model.metrics_base.H2OBinomialModelMetrics')
  # 2 level character response: expect bernoulli
  tree = h2o.import_frame(path=pyunit_utils.locate("smalldata/junit/test_tree_minmax.csv"))

  tree_model = h2o.gbm(x=tree[0:3], y=tree["response"], distribution="bernoulli", min_rows=1)
  #  assert isinstance(tree_model,h2o.model.binomial.H2OBinomialModel)
  assert(h2o.get_model(tree_model.model_id)._metrics_class,'h2o.model.metrics_base.H2OBinomialModelMetrics')
  # more than two integers for response: expect error
  try:
    cars_mod = h2o.gbm(x=cars[3:7], y=cars["cylinders"], distribution="bernoulli")
    assert False, "expected an error"
  except EnvironmentError:
    assert True
  # more than two character levels for response: expect error
  try:
    eco_model = h2o.gbm(x=eco[0:8], y=eco["Method"], distribution="bernoulli")
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  #Log.info("==============================")
  #Log.info("Multinomial Behavior")
  #Log.info("==============================")
  # more than two integers for response: expect multinomial
  cars_model = h2o.gbm(x=cars[3:7], y=cars["cylinders"].asfactor(), distribution="multinomial")
  assert(h2o.get_model(cars_model.model_id)._metrics_class,'h2o.model.metrics_base.H2OMultinomialModelMetrics')

  # more than two character levels for response: expect multinomial
  eco_model = h2o.gbm(x=eco[0:8], y=eco["Method"], distribution="multinomial")
  #  assert isinstance(eco_model,h2o.model.multinomial.H2OMultinomialModel)
  assert(h2o.get_model(eco_model.model_id)._metrics_class,'h2o.model.metrics_base.H2OMultinomialModelMetrics')
if __name__ == "__main__":
	pyunit_utils.standalone_test(distribution_behaviorGBM)
else:
	distribution_behaviorGBM()
