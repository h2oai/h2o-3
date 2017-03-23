from __future__ import print_function
from builtins import range
import math
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
import time


def airline_gbm_random_grid():
    '''
    This test is written to verify that random gridsearch honors the max_runtime_secs that we set in the
    search_criteria.  Assume that no max_runtime_secs is set in the model parameter and the models are
    built in sequence.  The max_runtime_secs of each model is set to

        min(model.parms.max_runtime_secs, remaining_secs still left in the search)

    This is how I am going to test to verify that the algo actually works:
    1.  Assume that grid search returns N models.
    2.  I will look at the model in sequence of time, meaning the first model built will be examined first, then
      the next one and so on until the N-1 model.
    3.  For the first N-1 models, sum all the runtime.
    4. search_critiera["max_runtime_secs"] > runtime, grid search is working fine.
    5.  I will not count the last model.  The reason is due to how often a model checks its max_runtime_secs.  If the
      model did not check its max_runtime_secs limit often enough, it may run a little longer than what the
      max_runtime_secs requirement.  However, if the model checks the max_runtime_secs very often, this will slow
      down its execution.  This tradeoff is performed by each developer for their own algos.
    '''
    air_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"),
                              destination_frame="air.hex")
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
                            'max_runtime_secs': 60,   # limit the runtime
                            'seed' : 1234,
                            }


    air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_params_tune, search_criteria=search_criteria_tune)
    air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, distribution="bernoulli", seed=1234)
    model_runtime_s = pyunit_utils.model_run_time_sorted_by_time(air_grid.models)
    allTime = sum(model_runtime_s)
    N_1Time = allTime-model_runtime_s[-1]
    print("Time taken to build all models is {0}.  Time taken to build N-1 models is {1}.  "
          "Search_criteria max_runtime_secs is {2}".format(allTime, N_1Time, search_criteria_tune["max_runtime_secs"]))
    assert N_1Time < search_criteria_tune["max_runtime_secs"], \
        "Random Gridsearch exceeds the max_runtime_secs criteria."

if __name__ == "__main__":
    pyunit_utils.standalone_test(airline_gbm_random_grid)
else:
    airline_gbm_random_grid()
