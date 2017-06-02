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


def stackedensemble_base_models_test():
    """This test checks the following:
    1) That passing in a list of models for base_models works.
    2) That passing in a list of models and model_ids results in the same stacked ensemble.
    """

    # Import training set
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_train_5k.csv"),
                            destination_frame="higgs_train_5k")

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # convert response to a factor
    train[y] = train[y].asfactor()

    # set number of folds
    nfolds = 5

    # train and cross-validate a GBM
    my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                          ntrees=10,
                                          nfolds=nfolds,
                                          fold_assignment="Modulo",
                                          keep_cross_validation_predictions=True,
                                          seed=1)
    my_gbm.train(x=x, y=y, training_frame=train)

    # train and cross-validate a RF
    my_rf = H2ORandomForestEstimator(ntrees=50,
                                     nfolds=nfolds,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)
    my_rf.train(x=x, y=y, training_frame=train)

    # Train a stacked ensemble using model ids in base_models
    stack1 = H2OStackedEnsembleEstimator(model_id="my_ensemble_binomial1",
                                         base_models=[my_gbm.model_id, my_rf.model_id])
    stack1.train(x=x, y=y, training_frame=train)

    # Train a stacked ensemble using models in base_models
    stack2 = H2OStackedEnsembleEstimator(model_id="my_ensemble_binomial2",
                                        base_models=[my_gbm, my_rf])

    stack2.train(x=x, y=y, training_frame=train)

    # Eval train AUC to assess equivalence
    assert stack1.auc() == stack2.auc()


if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_base_models_test)
else:
    stackedensemble_base_models_test()