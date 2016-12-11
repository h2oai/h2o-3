#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o
from h2o.grid.grid_search import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils


def stackedensemble_randomgrid_gaussian():

    # Import train and test datasets
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_train_5k.csv"),
                            destination_frame="higgs_train_5k")
    test = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_test_5k.csv"),
                           destination_frame="higgs_test_5k")

    # Identify predictors and response
    x = list(train.columns)
    y = "response"
    x.remove(y)

    # Even though this should be a classification problem,
    # we can leave the response as numeric to test regression
    distribution = "gaussian"

    # Specify GBM hyperparameters for the grid
    hyper_params = {
        "learn_rate": [0.1, 0.2],
        "max_depth": [2, 3, 4],
        "ntrees": [5, 10, 15]
    }
    search_criteria = {
        "strategy": "RandomDiscrete",
        "max_models": 3,
        "seed": 1234,
        "stopping_rounds": 3,
        "stopping_metric": "AUTO",
        "stopping_tolerance": 1e-2
    }

    # Train the grid
    grid = H2OGridSearch(
        model=H2OGradientBoostingEstimator,
        hyper_params=hyper_params,
        search_criteria=search_criteria
    )
    grid.train(
        x=x,
        y=y,
        training_frame=train,
        nfolds=5,
        fold_assignment="Modulo",
        keep_cross_validation_predictions=True,
        distribution=distribution,
        seed=5678
    )

    # Train the stacker
    stack = H2OStackedEnsembleEstimator(selection_strategy="choose_all", base_models=grid.model_ids)
    stack.train(model_id="higgs_gbm_ensemble", y=y, training_frame=train)

    predictions = stack.predict(test)  # training data
    print("preditions for ensemble are in: " + predictions.frame_id)

if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_randomgrid_gaussian)
else:
    stackedensemble_randomgrid_gaussian()
