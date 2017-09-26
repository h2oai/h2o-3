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


def stackedensemble_validation_frame_test():
    """This test checks the following:
    1) That passing in a validation_frame to h2o.stackedEnsemble does something (validation metrics exist).
    2) It should hopefully produce a better model (in the metalearning step).
    """

    # Import training set
    df = h2o.import_file(path=pyunit_utils.locate("smalldata/higgs/higgs_train_5k.csv"),
                         destination_frame="higgs_train_5k")
    test = h2o.import_file(path=pyunit_utils.locate("smalldata/higgs/higgs_test_5k.csv"),
                           destination_frame="higgs_test_5k")

    # Identify predictors and response
    x = df.columns
    y = "response"
    x.remove(y)

    # Convert response to a factor
    df[y] = df[y].asfactor()
    test[y] = test[y].asfactor()

    # Split off a validation_frame
    ss = df.split_frame(seed = 1)
    train = ss[0]
    valid = ss[1]

    # Set number of folds
    nfolds = 5

    # Train and cross-validate a GBM
    my_gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                          ntrees=10,
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

    # Train a stacked ensemble & check that validation metrics are missing
    stack1 = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id, my_rf.model_id])
    stack1.train(x=x, y=y, training_frame=train)
    assert(stack1.model_performance(valid=True) is None)

    # Train a stacked ensemble with a validation_frame & check that validation metrics exist & are correct type
    stack2 = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id, my_rf.model_id])
    stack2.train(x=x, y=y, training_frame=train, validation_frame=valid)
    assert(type(stack2.model_performance(valid=True)) == h2o.model.metrics_base.H2OBinomialModelMetrics)
    assert(type(stack2.auc(valid=True)) == float)


    # Compare test AUC (ensemble with validation_frame should not be worse)
    perf1 = stack1.model_performance(test_data=test)
    perf2 = stack2.model_performance(test_data=test)
    assert perf1.auc() >= perf2.auc()


if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_validation_frame_test)
else:
    stackedensemble_validation_frame_test()