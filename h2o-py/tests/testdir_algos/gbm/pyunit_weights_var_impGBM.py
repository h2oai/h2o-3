import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gbm import H2OGradientBoostingEstimator
import random


def weights_var_imp():
  def check_same(data1, data2, min_rows_scale):
    gbm1_regression = H2OGradientBoostingEstimator(min_rows=5,
                                                   ntrees=5,
                                                   max_depth=5)
    gbm1_regression.train(x=["displacement", "power", "weight", "acceleration", "year"],
                          y="economy",
                          training_frame=data1)

    gbm2_regression = H2OGradientBoostingEstimator(min_rows=5*min_rows_scale,
                                                   ntrees=5,
                                                   max_depth=5)
    gbm2_regression.train(x=["displacement", "power", "weight", "acceleration", "year", "weights"],
                          y="economy",
                          training_frame=data2,
                          weights_column="weights")

    gbm1_binomial = H2OGradientBoostingEstimator(min_rows=5,
                                                 distribution="bernoulli",
                                                 ntrees=5,
                                                 max_depth=5)
    gbm1_binomial.train(x=["displacement", "power", "weight", "acceleration", "year"],
                        y="economy_20mpg",
                        training_frame=data1)
    gbm2_binomial = H2OGradientBoostingEstimator(min_rows=5*min_rows_scale,
                                                 distribution="bernoulli",
                                                 ntrees=5,
                                                 max_depth=5)
    gbm2_binomial.train(x=["displacement", "power", "weight", "acceleration", "year", "weights"],
                        y="economy_20mpg",
                        training_frame=data2,
                        weights_column="weights")

    gbm1_multinomial = H2OGradientBoostingEstimator(min_rows=5,
                                                    distribution="multinomial",
                                                    ntrees=5,
                                                    max_depth=5)
    gbm1_multinomial.train(x=["displacement", "power", "weight", "acceleration", "year"],
                           y="cylinders",
                           training_frame=data1)

    gbm2_multinomial = H2OGradientBoostingEstimator(min_rows=5*min_rows_scale,
                                                    distribution="multinomial",
                                                    ntrees=5,
                                                    max_depth=5)
    gbm2_multinomial.train(x=["displacement", "power", "weight", "acceleration", "year", "weights"],
                           y="cylinders",
                           weights_column="weights", training_frame=data2)

    reg1_vi = gbm1_regression.varimp(return_list=True)
    reg2_vi = gbm2_regression.varimp(return_list=True)
    bin1_vi = gbm1_binomial.varimp(return_list=True)
    bin2_vi = gbm2_binomial.varimp(return_list=True)
    mul1_vi = gbm1_multinomial.varimp(return_list=True)
    mul2_vi = gbm2_multinomial.varimp(return_list=True)

    print "Varimp (regresson)   no weights vs. weights: {0}, {1}".format(reg1_vi, reg2_vi)
    print "Varimp (binomial)    no weights vs. weights: {0}, {1}".format(bin1_vi, bin2_vi)
    print "Varimp (multinomial) no weights vs. weights: {0}, {1}".format(mul1_vi, mul2_vi)

    for rvi1, rvi2 in zip(reg1_vi, reg2_vi): assert rvi1 == rvi1, "Expected vi's (regression)  to be the same, but got {0}, and {1}".format(rvi1, rvi2)
    for bvi1, bvi2 in zip(bin1_vi, bin2_vi): assert bvi1 == bvi1, "Expected vi's (binomial)    to be the same, but got {0}, and {1}".format(bvi1, bvi2)
    for mvi1, mvi2 in zip(mul1_vi, mul2_vi): assert mvi1 == mvi1, "Expected vi's (multinomial) to be the same, but got {0}, and {1}".format(mvi1, mvi2)

  h2o_cars_data = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
  h2o_cars_data["economy_20mpg"] = h2o_cars_data["economy_20mpg"].asfactor()
  h2o_cars_data["cylinders"] = h2o_cars_data["cylinders"].asfactor()

  # uniform weights same as no weights
  weight = random.randint(1,10)
  uniform_weights = [[weight for r in range(406)]]
  h2o_uniform_weights = h2o.H2OFrame(uniform_weights)
  h2o_uniform_weights.set_names(["weights"])
  h2o_data_uniform_weights = h2o_cars_data.cbind(h2o_uniform_weights)

  print "\n\nChecking that using uniform weights is equivalent to no weights:"
  check_same(h2o_cars_data, h2o_data_uniform_weights, weight)

  # zero weights same as removed observations
  zero_weights = [[0 if random.randint(0,1) else 1 for r in range(406)]]
  h2o_zero_weights = h2o.H2OFrame(zero_weights)
  h2o_zero_weights.set_names(["weights"])
  h2o_data_zero_weights = h2o_cars_data.cbind(h2o_zero_weights)
  h2o_data_zeros_removed = h2o_cars_data[h2o_zero_weights["weights"] == 1]

  print "\n\nChecking that using some zero weights is equivalent to removing those observations:"
  check_same(h2o_data_zeros_removed, h2o_data_zero_weights, 1)

  # doubled weights same as doubled observations
  doubled_weights = [[1 if random.randint(0,1) else 2 for r in range(406)]]
  h2o_doubled_weights = h2o.H2OFrame(doubled_weights)
  h2o_doubled_weights.set_names(["weights"])
  h2o_data_doubled_weights = h2o_cars_data.cbind(h2o_doubled_weights)

  doubled_data = h2o.as_list(h2o_cars_data, use_pandas=False)
  doubled_data = zip(*doubled_data)
  colnames = doubled_data.pop(0)
  for idx, w in enumerate(doubled_weights[0]):
    if w == 2: doubled_data.append(doubled_data[idx])
  doubled_data = zip(*doubled_data)
  h2o_data_doubled = h2o.H2OFrame(doubled_data)
  h2o_data_doubled.set_names(list(colnames))

  h2o_data_doubled["economy_20mpg"] = h2o_data_doubled["economy_20mpg"].asfactor()
  h2o_data_doubled["cylinders"] = h2o_data_doubled["cylinders"].asfactor()
  h2o_data_doubled_weights["economy_20mpg"] = h2o_data_doubled_weights["economy_20mpg"].asfactor()
  h2o_data_doubled_weights["cylinders"] = h2o_data_doubled_weights["cylinders"].asfactor()

  print "\n\nChecking that doubling some weights is equivalent to doubling those observations:"
  check_same(h2o_data_doubled, h2o_data_doubled_weights, 1)


if __name__ == "__main__":
    pyunit_utils.standalone_test(weights_var_imp)
else:
    weights_var_imp()
