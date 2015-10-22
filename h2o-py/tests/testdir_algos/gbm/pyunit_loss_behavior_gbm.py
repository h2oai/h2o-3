import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

from h2o.estimators.gbm import H2OGradientBoostingEstimator


def distribution_behavior_gbm():
  eco = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/ecology_model.csv"))
  # 0/1 response: expect gaussian
  eco_model = H2OGradientBoostingEstimator()
  eco_model.train(x=range(2,13), y="Angaus", training_frame=eco)
  # more than 2 integers for response: expect gaussian
  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars.csv"))
  cars_model = H2OGradientBoostingEstimator()
  cars_model.train(x=range(3,7),y="cylinders", training_frame=cars)

  # 0/1 response: expect gaussian
  eco_model = H2OGradientBoostingEstimator(distribution="gaussian")
  eco_model.train(x=range(2,13), y="Angaus", training_frame=eco)
  # character response: expect error
  try:
    eco_model.train(x=range(1,8), y="Method", training_frame=eco)
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  # 0/1 response: expect bernoulli
  eco_model = H2OGradientBoostingEstimator(distribution="bernoulli")
  eco["Angaus"] = eco["Angaus"].asfactor()
  eco_model.train(x=range(2,13), y="Angaus", training_frame=eco)
  # 2 level character response: expect bernoulli
  tree = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/test_tree_minmax.csv"))
  tree_model=eco_model
  tree_model.min_rows = 1
  tree_model.train(range(3),y="response",training_frame=tree)
  # more than two integers for response: expect error
  try:
    cars_mod = H2OGradientBoostingEstimator(distribution="bernoulli")
    cars_mod.train(x=range(3,7), y="cylinders", training_frame=cars)
    assert False, "expected an error"
  except EnvironmentError:
    assert True
  # more than two character levels for response: expect error
  try:
    eco_model = H2OGradientBoostingEstimator(distribution="bernoulli")
    eco_model.train(x=range(8), y="Method", training_frame=eco)
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  #Log.info("==============================")
  #Log.info("Multinomial Behavior")
  #Log.info("==============================")
  # more than two integers for response: expect multinomial
  cars["cylinders"] = cars["cylinders"].asfactor()
  cars_model = H2OGradientBoostingEstimator(distribution="multinomial")
  cars_model.train(range(3,7), y="cylinders", training_frame=cars)
  cars_model = H2OGradientBoostingEstimator(distribution="multinomial")
  cars_model.train(x=range(3,7), y="cylinders", training_frame=cars)
  # more than two character levels for response: expect multinomial
  eco_model = H2OGradientBoostingEstimator(distribution="multinomial")
  eco_model.train(x=range(8), y="Method", training_frame=eco)




if __name__ == "__main__":
  pyunit_utils.standalone_test(distribution_behavior_gbm)
else:
  distribution_behavior_gbm()
