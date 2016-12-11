#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils


def airline_gbm_random_grid():
    air_hex = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))
    myX = ["DayofMonth", "DayOfWeek"]

    hyper_parameters = {
        "learn_rate": [0.1, 0.2],
        "max_depth": [2, 3, 4],
        "ntrees": [5, 10, 15]
    }

    search_crit = {
        "strategy": "RandomDiscrete",
        "max_models": 5,
        "seed": 1234305963,
        "stopping_rounds": 3,
        "stopping_metric": "AUTO",
        "stopping_tolerance": 1e-2
    }

    air_grid = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters, search_criteria=search_crit)
    air_grid.train(x=myX, y="IsDepDelayed", training_frame=air_hex, nfolds=5, fold_assignment="Modulo",
                   keep_cross_validation_predictions=True, distribution="bernoulli", seed=5678245209)

    assert len(air_grid.get_grid()) == 5
    print(air_grid.get_grid("logloss"))

    stacker = H2OStackedEnsembleEstimator(selection_strategy="choose_all", base_models=air_grid.model_ids)
    stacker.train(model_id="my_ensemble", y="IsDepDelayed", training_frame=air_hex)
    predictions = stacker.predict(air_hex)  # training data
    print("Predictions for ensemble are in: " + predictions.frame_id)

    # Check that the model can be retrieved
    assert stacker.model_id == "my_ensemble"
    modelcopy = h2o.get_model(stacker.model_id)
    assert modelcopy is not None
    assert modelcopy.model_id == "my_ensemble"


if __name__ == "__main__":
    pyunit_utils.standalone_test(airline_gbm_random_grid)
else:
    airline_gbm_random_grid()
