#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils

def stackedensemble_binary_test():
    # Import a sample binary outcome train/test set into H2O
    train = h2o.import_file(pyunit_utils.locate("smalldata/higgs/higgs_train_10k.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/testng/higgs_test_5k.csv"))

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # For binary classification, response should be a factor
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    # Number of CV folds (to generate level-one data for stacking)
    nfolds = 5

    # 1. Generate a 2-model ensemble (GBM + RF)

    # Train and cross-validate a GBM
    my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                          ntrees=10,
                                          max_depth=3,
                                          min_rows=2,
                                          learn_rate=0.2,
                                          nfolds=nfolds,
                                          fold_assignment="Modulo",
                                          keep_cross_validation_predictions=True,
                                          seed=1)
    my_gbm.train(x=x, y=y, training_frame=train)


    # Train and cross-validate a RF
    my_rf = H2ORandomForestEstimator(ntrees=50,
                                     nfolds=nfolds,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)
    my_rf.train(x=x, y=y, training_frame=train)


    # Train a stacked ensemble using the GBM and DRF above
    ensemble = H2OStackedEnsembleEstimator(model_id="my_ensemble_binomial",
                                           base_models=[my_gbm.model_id, my_rf.model_id])
    ensemble.train(x=x, y=y, training_frame=train)

    #Predict in ensemble in Py client
    preds_py =  ensemble.predict(test)

    #Load binary model and predict
    bin_model = h2o.load_model(pyunit_utils.locate("smalldata/binarymodels/stackedensemble/ensemble_higgs"))
    preds_bin = bin_model.predict(test)

    #Predictions from model in Py and binary model should be the same
    pred_diff = preds_bin - preds_py
    assert pred_diff["p0"].max() < 1e-11
    assert pred_diff["p1"].max() < 1e-11
    assert pred_diff["p0"].min() > -1e-11
    assert pred_diff["p1"].min() > -1e-11

if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_binary_test)
else:
    stackedensemble_binary_test()