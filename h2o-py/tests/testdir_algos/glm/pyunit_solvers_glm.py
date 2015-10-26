import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_solvers():
  training_data = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
  predictors = ["displacement","power","weight","acceleration","year"]

  for solver in ["AUTO", "IRLSM", "L_BFGS", "COORDINATE_DESCENT_NAIVE", "COORDINATE_DESCENT"]:
    print "Solver = {0}".format(solver)
    for family in ["binomial", "gaussian", "poisson", "tweedie", "gamma"]:
      if   family == 'binomial': response_col = "economy_20mpg"
      elif family == 'gaussian': response_col = "economy"
      else:                      response_col = "cylinders"
      print "Family = {0}".format(family)

      if   family == 'binomial': training_data[response_col] = training_data[response_col].asfactor()
      else:                      training_data[response_col] = training_data[response_col].asnumeric()

      model = H2OGeneralizedLinearEstimator(family=family, alpha=0, Lambda=1e-5, solver=solver)
      model.train(x=predictors, y=response_col, training_frame=training_data)



if __name__ == "__main__":
  pyunit_utils.standalone_test(glm_solvers)
else:
  glm_solvers()
