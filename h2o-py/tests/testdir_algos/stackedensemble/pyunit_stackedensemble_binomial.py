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


def stackedensemble_binomial_test():
    """This test check the following (for binomial classification):
    1) That H2OStackedEnsembleEstimator executes w/o erros on a 2-model 'manually constructed ensemble.
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

    print(train.summary())

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # convert response to a factor
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    # set number of folds
    nfolds = 5

    # train and cross-validate a GBM
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

    # evaluate the performance
    perf_gbm_train = my_gbm.model_performance(train=True)
    perf_gbm_test = my_gbm.model_performance(test_data=test)
    print("GBM training performance: ")
    print(perf_gbm_train)
    print("GBM test performance: ")
    print(perf_gbm_test)

    # train and cross-validate a RF
    my_rf = H2ORandomForestEstimator(ntrees=50, 
                                     nfolds=nfolds, 
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True, 
                                     seed=1)

    my_rf.train(x=x, y=y, training_frame=train)

    # evaluate performance
    perf_rf_train = my_rf.model_performance(train=True)
    perf_rf_test = my_rf.model_performance(test_data=test)
    print("RF training performance: ")
    print(perf_rf_train)
    print("RF test performance: ")
    print(perf_rf_test)

    # Train a stacked ensemble using the GBM and GLM above
    stack = H2OStackedEnsembleEstimator(model_id="my_ensemble_binomial",
                                        base_models=[my_gbm.model_id,  my_rf.model_id])

    stack.train(x=x, y=y, training_frame=train, validation_frame=test)  # also test that validation_frame is working

    # check that prediction works
    pred = stack.predict(test_data=test)
    assert pred.nrow == test.nrow, "expected " + str(pred.nrow) + " to be equal to " + str(test.nrow)
    assert pred.ncol == 3, "expected " + str(pred.ncol) + " to be equal to 3 but it was equal to " + str(pred.ncol)

    # Evaluate ensemble performance
    perf_stack_train = stack.model_performance()
    perf_stack_test = stack.model_performance(test_data=test)

    # Check that stack perf is better (bigger) than the best(biggest) base learner perf:
    # Training AUC
    baselearner_best_auc_train = max(perf_gbm_train.auc(), perf_rf_train.auc())
    stack_auc_train = perf_stack_train.auc()
    print("Best Base-learner Training AUC:  {0}".format(baselearner_best_auc_train))
    print("Ensemble Training AUC:  {0}".format(stack_auc_train))
    assert stack_auc_train > baselearner_best_auc_train, "expected stack_auc_train would be greater than " \
                                                         " found it wasn't baselearner_best_auc_train"

    # Test AUC
    baselearner_best_auc_test = max(perf_gbm_test.auc(), perf_rf_test.auc())
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
    pyunit_utils.standalone_test(stackedensemble_binomial_test)
else:
    stackedensemble_binomial_test()


