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
    # search_criteria directs how to do grid search.
    # 1). grid search can stop early if the early stopping conditions specified by
    #   stopping_metric/stopping_tolerance/stopping_rounds
    # 2). grid search will stop if it takes longer than max_runtime_secs
    # 3). grid search will stop if it has collected max_models in its array.
    #
    # grid search stops correctly if any of the three conditions are satisfied
    search_criteria_tune = {'strategy': "RandomDiscrete",
                            'max_runtime_secs': 600,   # limit the runtime to 10 minutes to hit more stopping conditions
                            'max_models': 5,            # build no more than 5 models
                            'seed' : 1234,
                            'stopping_rounds' : 5,
                            'stopping_metric' : "AUC",
                            'stopping_tolerance': 1e-3
                            }


    air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_params_tune, search_criteria=search_criteria_tune)
    starttime = time.time()
    air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, nfolds=5, fold_assignment='Modulo', keep_cross_validation_predictions=True, distribution="bernoulli", seed=1234)
    runtime = time.time()-starttime

    # check stopping condition 3), max_models
    correct_stopping_condition = len(air_grid.get_grid()) == search_criteria_tune["max_models"]

    # if false, check stopping condition 2), max_runtime_secs
    if not(correct_stopping_condition):
        correct_stopping_condition = runtime >= search_criteria_tune["max_runtime_secs"]

    # if false, check stopping condition 1), early stopping has occurred.
    if not(correct_stopping_condition):
        for eachModel in air_grid.models:
            metric_list = pyunit_utils.extract_scoring_history_field(eachModel, "training_auc")
            if pyunit_utils.evaluate_early_stopping(metric_list, search_criteria_tune["stopping_rounds"],
                                                    search_criteria_tune["stopping_tolerance"], True):
                correct_stopping_condition=True
                break

    assert correct_stopping_condition, "Grid search did not find a model that fits the search_criteria_tune."
    print(air_grid.get_grid("logloss"))

    stacker = H2OStackedEnsembleEstimator(base_models=air_grid.model_ids)
    stacker.train(model_id="my_ensemble", y="IsDepDelayed", training_frame=air_hex)
    predictions = stacker.predict(air_hex)  # training data
    print("preditions for ensemble are in: " + predictions.frame_id)

if __name__ == "__main__":
    pyunit_utils.standalone_test(airline_gbm_random_grid)
else:
    airline_gbm_random_grid()
