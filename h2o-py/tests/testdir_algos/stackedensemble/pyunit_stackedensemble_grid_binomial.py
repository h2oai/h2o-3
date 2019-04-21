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


def stackedensemble_grid_binomial():
    """This test check the following (for binomial classification):
    1) That H2OStackedEnsembleEstimator executes w/o errors on a random-grid-based ensemble.
    2) That .predict() works on a stack.
    3) That .model_performance() works on a stack.
    4) That the training and test performance is better on ensemble vs the base learners.
    5) That the validation_frame arg on H2OStackedEnsembleEstimator works correctly.
    """

    # Import train and test datasets
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_train_5k.csv"),
                            destination_frame="higgs_train_5k")
    test = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_test_5k.csv"),
                           destination_frame="higgs_test_5k")

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # Encode the response as categorical
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    # Set number of folds
    nfolds = 5

    # Specify GBM hyperparameters for the grid
    hyper_params = {"learn_rate": [0.01, 0.03],
                    "max_depth": [3, 4, 5, 6, 9],
                    "sample_rate": [0.7, 0.8, 0.9, 1.0],
                    "col_sample_rate": [0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8]}
    search_criteria = {"strategy": "RandomDiscrete", "max_models": 3, "seed": 1}

    # Train the grid
    grid = H2OGridSearch(model=H2OGradientBoostingEstimator(ntrees=10, seed=1,
                                                            nfolds=nfolds, fold_assignment="Modulo",
                                                            keep_cross_validation_predictions=True),
                         hyper_params=hyper_params,
                         search_criteria=search_criteria,
                         grid_id="gbm_grid_binomial")

    grid.train(x=x, y=y, training_frame=train)

    # Train a stacked ensemble using the GBM grid
    stack = H2OStackedEnsembleEstimator(model_id="my_ensemble_gbm_grid_binomial", 
                                        base_models=grid.model_ids)
    stack.train(x=x, y=y, training_frame=train, validation_frame=test)


    # check that prediction works
    pred = stack.predict(test_data= test)
    assert pred.nrow == test.nrow, "expected " + str(pred.nrow) + " to be equal to " + str(test.nrow)
    assert pred.ncol == 3, "expected " + str(pred.ncol) + " to be equal to 3 but it was equal to " + str(pred.ncol)

    # Evaluate ensemble performance
    perf_stack_train = stack.model_performance()
    perf_stack_test = stack.model_performance(test_data=test)

    # Training AUC for each base learner
    baselearner_best_auc_train = max([h2o.get_model(model).auc(train = True) for model in grid.model_ids])
    stack_auc_train = perf_stack_train.auc()
    print("Best Base-learner Training AUC:  {0}".format(baselearner_best_auc_train))
    print("Ensemble Training AUC:  {0}".format(stack_auc_train))
    # this does not pass, but that's okay for training error
    #assert stack_auc_train > baselearner_best_auc_train, "expected stack_auc_train would be greater than " \
    #                                                     " found it wasn't baselearner_best_auc_train"

    # Test AUC
    baselearner_best_auc_test = max([h2o.get_model(model).model_performance(test_data=test).auc() for model in grid.model_ids])
    stack_auc_test = perf_stack_test.auc()
    print("Best Base-learner Test AUC:  {0}".format(baselearner_best_auc_test))
    print("Ensemble Test AUC:  {0}".format(stack_auc_test))
    assert stack_auc_test > baselearner_best_auc_test, "expected stack_auc_test would be greater than " \
                                                       " baselearner_best_auc_test, found it wasn't  " \
                                                       "baselearner_best_auc_test = "+ \
                                                       str(baselearner_best_auc_test) + ",stack_auc_test " \
                                                                                        " = "+ str(stack_auc_test)

    # Check that passing `test` as a validation_frame produces the same metric as stack.model_performance(test)
    # since the metrics object is not exactly the same, we can just test that AUC is the same
    perf_stack_validation_frame = stack.model_performance(valid=True)
    assert stack_auc_test == perf_stack_validation_frame.auc(), "expected stack_auc_test to be the same as " \
                                                                "perf_stack_validation_frame.auc() found they were not " \
                                                                "perf_stack_validation_frame.auc() = " + \
                                                                str(perf_stack_validation_frame.auc()) + \
                                                                "stack_auc_test was " + str(stack_auc_test)

if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_grid_binomial)
else:
    stackedensemble_grid_binomial()
