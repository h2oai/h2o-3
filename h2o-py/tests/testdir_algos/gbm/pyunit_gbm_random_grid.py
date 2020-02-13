#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o
import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from h2o.exceptions import H2OResponseError
from tests import pyunit_utils


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
    air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, nfolds=5, fold_assignment='Modulo', keep_cross_validation_predictions=True, distribution="bernoulli", seed=5678)

    air_grid.show()
    assert(len(air_grid.get_grid())==5)
    print(air_grid.get_grid("logloss"))


    air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters, search_criteria=search_crit)
    air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, distribution="bernoulli")
    assert(len(air_grid.get_grid())==5)
    print(air_grid.get_grid("logloss"))

    # added this part to check h2o.get_grid is working properly
    fetch_grid = h2o.get_grid(str(air_grid.grid_id))
    assert len(air_grid.get_grid())==len(fetch_grid.get_grid())


    ################################################################################
    # PUBDEV-5145: make sure we give a good error message for JSON parse failures, like range() under 3.6
    hyper_parameters['max_depth'] = range(2, 4)
    search_crit['max_models'] = 1

    if sys.version_info[0] < 3:
        # no exception
        air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters, search_criteria=search_crit)
        air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, nfolds=5, fold_assignment='Modulo', keep_cross_validation_predictions=True, distribution="bernoulli", seed=5678)
    else:
        # MalformedJsonException in Java; check for the right error message in Python
        got_exception = False
        exc = None
        try:
            air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters, search_criteria=search_crit)
            air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, nfolds=5, fold_assignment='Modulo', keep_cross_validation_predictions=True, distribution="bernoulli", seed=5678)
        except H2OResponseError as e:
            got_exception = True
            exc = e
        assert(type(exc) == H2OResponseError)
        print("Got an H2OResponseError, as expected with 3.x")
        assert("Error: Can't parse the hyper_parameters dictionary" in str(exc))
        assert(got_exception)


    hyper_parameters['max_depth'] = 1
    search_crit['max_models'] = [1, 3]  # expecting an int
    # IllegalStateException in Java; check for the right error message in Python
    got_exception = False
    exc = None
    try:
        air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters, search_criteria=search_crit)
        air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, nfolds=5, fold_assignment='Modulo', keep_cross_validation_predictions=True, distribution="bernoulli", seed=5678)
    except H2OResponseError as e:
        got_exception = True
        exc = e
    assert(type(exc) == H2OResponseError)
    print("Got an H2OResponseError, as expected with 3.x")
    assert("Error: Can't parse the search_criteria dictionary" in str(exc))
    assert(got_exception)

if __name__ == "__main__":
    pyunit_utils.standalone_test(airline_gbm_random_grid)
else:
    airline_gbm_random_grid()
