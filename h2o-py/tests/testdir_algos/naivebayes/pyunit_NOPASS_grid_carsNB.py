from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import random
import copy
from h2o.estimators.naive_bayes import H2ONaiveBayesEstimator
from h2o.grid.grid_search import H2OGridSearch

def grid_cars_NB():

    cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
    r = cars[0].runif(seed=42)
    train = cars[r > .2]

    problem = random.sample(["binomial","multinomial"],1)
    predictors = ["displacement","power","weight","acceleration","year"]
    if problem == "binomial":
        response_col = "economy_20mpg"
    else:
        response_col = "cylinders"

    print("Predictors: {0}".format(predictors))
    print("Response: {0}".format(response_col))

    print("Converting the response column to a factor...")
    train[response_col] = train[response_col].asfactor()

    max_runtime_secs = 10  # this will return full NB model
    # the field manual_model._model_json['output']['cross_validation_metrics_summary'].cell_values will be empty
    max_runtime_secs = 0.001

    model_params = {'compute_metrics': True, 'fold_assignment': 'AUTO', 'laplace': 8.3532975, 'nfolds': 2}

    cars_nb = H2ONaiveBayesEstimator(**model_params)
    cars_nb.train(x=predictors, y=response_col, training_frame=train, max_runtime_secs=max_runtime_secs)

    if len(cars_nb._model_json['output']['cross_validation_metrics_summary'].cell_values) > 0:
        print("Pass test.  Complete metrics returned.")
    else:
        print("Failed test.  Model metrics is missing.")

if __name__ == "__main__":
    pyunit_utils.standalone_test(grid_cars_NB)
else:
    grid_cars_NB()