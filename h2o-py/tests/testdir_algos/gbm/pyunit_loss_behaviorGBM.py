import sys
sys.path.insert(1, "../../../")
import h2o, tests

def distribution_behaviorGBM(ip,port):
  
  

  #Log.info("==============================")
  #Log.info("Default Behavior - Gaussian")
  #Log.info("==============================")
  eco = h2o.import_file(path=h2o.locate("smalldata/gbm_test/ecology_model.csv"))
  # 0/1 response: expect gaussian
  eco_model = h2o.gbm(x=eco[2:13], y=eco["Angaus"])
  assert isinstance(eco_model,h2o.model.regression.H2ORegressionModel)
  # more than 2 integers for response: expect gaussian
  cars = h2o.import_file(path=h2o.locate("smalldata/junit/cars.csv"))
  cars_model = h2o.gbm(x=cars[3:7], y=cars["cylinders"])
  assert isinstance(cars_model,h2o.model.regression.H2ORegressionModel)

  #Log.info("==============================")
  #Log.info("Gaussian Behavior")
  #Log.info("==============================")
  # 0/1 response: expect gaussian
  eco_model = h2o.gbm(x=eco[2:13], y=eco["Angaus"], distribution="gaussian")
  assert isinstance(eco_model,h2o.model.regression.H2ORegressionModel)
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
  assert isinstance(eco_model,h2o.model.binomial.H2OBinomialModel)
  # 2 level character response: expect bernoulli
  tree = h2o.import_file(path=h2o.locate("smalldata/junit/test_tree_minmax.csv"))
  tree_model = h2o.gbm(x=tree[0:3], y=tree["response"], distribution="bernoulli", min_rows=1)
  assert isinstance(tree_model,h2o.model.binomial.H2OBinomialModel)
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
  assert isinstance(cars_model,h2o.model.multinomial.H2OMultinomialModel)
  # more than two character levels for response: expect multinomial
  eco_model = h2o.gbm(x=eco[0:8], y=eco["Method"], distribution="multinomial")
  assert isinstance(eco_model,h2o.model.multinomial.H2OMultinomialModel)

if __name__ == "__main__":
    tests.run_test(sys.argv, distribution_behaviorGBM)

