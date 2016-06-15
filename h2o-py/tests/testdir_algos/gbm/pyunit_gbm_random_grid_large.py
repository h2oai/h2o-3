from __future__ import print_function
from builtins import map
from builtins import str
from builtins import range
from collections import OrderedDict
import sys
import math
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
import itertools
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator

def airline_gbm_random_grid():
  air_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"), destination_frame="air.hex")
  myX = ["Year","Month","CRSDepTime","UniqueCarrier","Origin","Dest"]

  # create hyperameter and search criteria lists (ranges are inclusive..exclusive))
  hyper_params_tune = {'max_depth' : list(range(1,10+1,1)),
                'sample_rate': [x/100. for x in range(20,101)],
                'col_sample_rate' : [x/100. for x in range(20,101)],
                'col_sample_rate_per_tree': [x/100. for x in range(20,101)],
                'col_sample_rate_change_per_level': [x/100. for x in range(90,111)],
                'min_rows': [2**x for x in range(0,int(math.log(air_hex.nrow,2)-1)+1)],
                'nbins': [2**x for x in range(4,11)],
                'nbins_cats': [2**x for x in range(4,13)],
                'min_split_improvement': [0,1e-8,1e-6,1e-4],
                'histogram_type': ["UniformAdaptive","QuantilesGlobal","RoundRobin"]}

  search_criteria_tune = {'strategy': "RandomDiscrete",
                   'max_runtime_secs': 600,  ## limit the runtime to 10 minutes
                   'max_models': 5,  ## build no more than 5 models
                   'seed' : 1234,
                   'stopping_rounds' : 5,
                   'stopping_metric' : "AUC",
                   'stopping_tolerance': 1e-3
                   }

  air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_params_tune, search_criteria=search_criteria_tune)
  air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, distribution="bernoulli", seed=1234)

  assert(len(air_grid.get_grid())==5)
  print(air_grid.get_grid("logloss"))

if __name__ == "__main__":
  pyunit_utils.standalone_test(airline_gbm_random_grid)
else:
  airline_gbm_random_grid()
