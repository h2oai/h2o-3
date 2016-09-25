from __future__ import print_function
from builtins import range
import sys, os
sys.path.insert(1, os.path.join("..","..",".."))
import h2o
from tests import pyunit_utils
import random
from h2o.estimators.deepwater import H2ODeepWaterEstimator

def cv_airlines():

  # read in the dataset and construct training set (and validation set)
  df =  h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))

  # pick the predictors and the correct response column
  predictors = ["Year","Month","DayofMonth","DayOfWeek","CRSDepTime","CRSArrTime","UniqueCarrier","FlightNum"]
  response_col = "IsDepDelayed"

  dl = H2ODeepWaterEstimator(nfolds=3, hidden=[200,200], activation="Rectifier",  # network (fully-connected)
	 		     hidden_dropout_ratios=[0.1,0.1], input_dropout_ratio=0.0,  # regularization
			     rate=5e-3, rate_annealing=1e-6,  # learning rate
			     momentum_start=0.9, momentum_stable=0.99, momentum_ramp=1e7,  # momentum
		             epochs=100, stopping_rounds=4, train_samples_per_iteration=30000,  # early stopping
			     mini_batch_size=32, score_duty_cycle=0.25, score_interval=1)  # score often for early stopping

  dl.train(x=predictors, y=response_col, training_frame=df)
  print(dl.show())

if __name__ == "__main__":
  pyunit_utils.standalone_test(cv_airlines)
else:
  cv_airlines()
