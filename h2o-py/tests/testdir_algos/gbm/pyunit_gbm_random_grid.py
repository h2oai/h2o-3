from __future__ import print_function
from builtins import map
from builtins import str
from builtins import range
from collections import OrderedDict
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import itertools
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def airline_gbm_random_grid():
  air_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"), destination_frame="air.hex")
  myX = ["DayofMonth","DayOfWeek"]

  hyper_parameters = {
      'learn_rate':[0.1,0.2],
      'max_depth':[2,3,4],
      'ntrees':[5,10,15]
  }

  search_crit = {'strategy': "RandomDiscrete",
                   'max_models': 5,
                   'seed' : 1234,
                   'stopping_rounds' : 3,
                   'stopping_metric' : "AUTO",
                   'stopping_tolerance': 1e-2
                   }

  air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters, search_criteria=search_crit)
  air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, distribution="bernoulli")
  assert(len(air_grid.get_grid())==5)
  print(air_grid.get_grid("logloss"))

  # added this part to check h2o.get_grid is working properly
  fetch_grid = h2o.get_grid(str(air_grid.grid_id))
  assert len(air_grid.get_grid())==len(fetch_grid.get_grid())


if __name__ == "__main__":
  pyunit_utils.standalone_test(airline_gbm_random_grid)
else:
  airline_gbm_random_grid()
