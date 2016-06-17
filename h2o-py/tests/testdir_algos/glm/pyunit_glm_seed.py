from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import random
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

def glm_seed():

  # read in the dataset and construct training set (and validation set)
  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
  # set the response column, predictors, and family type
  y = "economy_20mpg"
  predictors = ["displacement","power","weight","acceleration","year"]
  family = "binomial"

  #For binary classification, response should be a factor
  cars[y] = cars[y].asfactor()

  # Train a GLM with the seed set to 1234
  h2oglm_1 =  H2OGeneralizedLinearEstimator(
          family = "binomial",
          alpha  = 1.0,
          lambda_search = True,
          max_iterations  = 1000,
          nfolds  = 5,
          seed = 1234,
          max_active_predictors = 200)

  h2oglm_1.train(x = predictors, y = y, training_frame  = cars, family = family)


  h2oglm_2 =  H2OGeneralizedLinearEstimator(
          family = "binomial",
          alpha  = 1.0,
          lambda_search = True,
          max_iterations  = 1000,
          nfolds  = 5,
          seed = 1234,
          max_active_predictors = 200)

  h2oglm_2.train(x = predictors, y = y, training_frame  = cars, family = family)

  assert h2oglm_1.coef() == h2oglm_2.coef()

  # check that they are not equal when the seed isn't set

  h2oglm_3 =  H2OGeneralizedLinearEstimator(
          family = "binomial",
          alpha  = 1.0,
          lambda_search = True,
          max_iterations  = 1000,
          nfolds  = 5,
          max_active_predictors = 200)

  h2oglm_3.train(x = predictors, y = y, training_frame  = cars, family = family)


  h2oglm_4 =  H2OGeneralizedLinearEstimator(
          family = "binomial",
          alpha  = 1.0,
          lambda_search = True,
          max_iterations  = 1000,
          nfolds  = 5,
          max_active_predictors = 200)

  h2oglm_4.train(x = predictors, y = y, training_frame  = cars, family = family)

  assert h2oglm_3.coef() != h2oglm_4.coef()


if __name__ == "__main__":
  pyunit_utils.standalone_test(glm_seed)
else:
  glm_seed()

