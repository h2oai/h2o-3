#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import sys
sys.path.insert(1,"../../../")  # for tests to be run standalone

from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from h2o.grid.grid_search import H2OGridSearch
from tests import pyunit_utils

def stackedensemble_modelsummary_test():
    """This test checks that the stacked ensemble model_summary() works. It uses the stacked ensemble demo code.
    """

    train = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_train_10k.csv")
    test = h2o.import_file("https://s3.amazonaws.com/erin-data/higgs/higgs_test_5k.csv")

    x = train.columns
    y = "response"
    x.remove(y)

    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    nfolds = 5

    my_gbm = H2OGradientBoostingEstimator(distribution='bernoulli',
                                          ntrees=10,
                                          max_depth=3,
                                          min_rows=2,
                                          learn_rate=0.2,
                                          nfolds=nfolds,
                                          fold_assignment='Modulo',
                                          keep_cross_validation_predictions=True,
                                          seed=1)

    my_gbm.train(x=x, y=y, training_frame=train)

    my_rf = H2ORandomForestEstimator(ntrees=50,
                                     nfolds=nfolds,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)

    my_rf.train(x=x, y=y, training_frame=train)

    ensemble = H2OStackedEnsembleEstimator(model_id="my_ensemble_binomial",
                                           base_models=[my_gbm.model_id, my_rf.model_id])

    ensemble.train(x=x, y=y, training_frame=train)

    # these will print out the model summary information
    mod_summary = ensemble.model_summary()
    mod_summary_details = ensemble.model_summary(base_model_detail=True)

    assert mod_summary is None # should return None
    assert mod_summary_details is None # should return None


if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_modelsummary_test)
else:
    stackedensemble_modelsummary_test()