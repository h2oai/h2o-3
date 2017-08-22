#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o
import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
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

    assert(len(air_grid.get_grid())==5)
    print(air_grid.get_grid("logloss"))



    stacker = H2OStackedEnsembleEstimator(base_models=air_grid.model_ids)
    print("created H2OStackedEnsembleEstimator")
    stacker.train(model_id="my_ensemble", y="IsDepDelayed", training_frame=air_hex)
    print("trained H2OStackedEnsembleEstimator")
    predictions = stacker.predict(air_hex)  # training data
    print("predictions for ensemble are in: " + predictions.frame_id)

    # Check that the model can be retrieved
    assert stacker.model_id == "my_ensemble"
    modelcopy = h2o.get_model(stacker.model_id)
    assert modelcopy is not None
    assert modelcopy.model_id == "my_ensemble"

    # golden test for ensemble predictions:
    assert round(predictions[0, "YES"], 4) == 0.4327, "Expected prediction for row: {0} to be: {1}; got: {2} instead.".format(0, 0.4327, round(predictions[0, "YES"], 4))
    assert round(predictions[1, "YES"], 4) == 0.5214, "Expected prediction for row: {0} to be: {1}; got: {2} instead.".format(1, 0.5214, round(predictions[1, "YES"], 4))
    assert round(predictions[2, "YES"], 4) == 0.4666, "Expected prediction for row: {0} to be: {1}; got: {2} instead.".format(2, 0.4666, round(predictions[2, "YES"], 4))

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
