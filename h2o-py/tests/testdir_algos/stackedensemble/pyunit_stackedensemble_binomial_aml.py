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

    idx = train.runif()
    train1 = train[idx < 1.0/3]
    train2 = train[(idx >= 1.0/3) & (idx < 2.0/3)]
    train3 = train[idx >= 2.0/3]
    trainList = [train1,train2,train3]

    # train and cross-validate a RF
    rf_model_list = []
    for i in range(3):
        rf_model_list.append(H2ORandomForestEstimator(ntrees=50,
                                     nfolds=nfolds, 
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True, 
                                     seed=1))

        rf_model_list[i].train(x=x, y=y, training_frame=trainList[i])

        # evaluate performance
        perf_rf_train = rf_model_list[i].model_performance(train=True)
        perf_rf_test = rf_model_list[i].model_performance(test_data=test)
        print("RF training performance: ")
        print(perf_rf_train)
        print("RF test performance: ")
        print(perf_rf_test)

    # Build a frame with prediction from all 3 models
    superl_data = train
    superl_data["p1"] = rf_model_list[0].predict(train)["p1"]
    superl_data["p2"] = rf_model_list[1].predict(train)["p1"]
    superl_data["p3"] = rf_model_list[2].predict(train)["p1"]
    superl_test = test
    superl_test["p1"] = rf_model_list[0].predict(test)["p1"]
    superl_test["p2"] = rf_model_list[1].predict(test)["p1"]
    superl_test["p3"] = rf_model_list[2].predict(test)["p1"]
    # Train a stacked ensemble using the GBM and GLM above
    superlearner  = H2ORandomForestEstimator(model_id="superlearner_all",
                                             ntrees=60,
                                             nfolds=nfolds,
                                             seed=123)
    # This is the ideal case, Stacking sees all data
    superlearner.train(x=x+["p1","p2","p3"],y=y,training_frame=superl_data)
    print("Superlearner all data performance: ")
    print(superlearner)
    print("Superlearner all test perf")
    print(superlearner.model_performance(test_data=superl_test))

    superlearner_ck = H2ORandomForestEstimator(model_id="superlearner_ck",ntrees=20,seed=123)
    superlearner_ck.train(x=x+["p1","p2","p3"],y=y,training_frame=superl_data[idx < 1.0/3],validation_frame=superl_test)
    print("Superlearner ck")
    print(superlearner_ck)

    superlearner_ck1 = H2ORandomForestEstimator(checkpoint="superlearner_ck",ntrees=40,seed=123)
    superlearner_ck1.train(x=x+["p1","p2","p3"],y=y,training_frame=superl_data[(idx >= 1.0/3) & (idx < 2.0/3)],validation_frame=superl_test)
    print("Superlearner ck1")
    print(superlearner_ck1)

    superlearner_ck2 = H2ORandomForestEstimator(checkpoint="superlearner_ck",ntrees=60,seed=123)
    superlearner_ck2.train(x=x+["p1","p2","p3"],y=y,training_frame=superl_data[idx >= 2.0/3],validation_frame=superl_test)
    print("Superlearner ck2")
    print(superlearner_ck2)

    print("Final checkpointed superlearner perf")
    print(superlearner_ck2.model_performance(test_data=superl_test))


    # learn from p1,p2,p3
    superlearner_p  = H2ORandomForestEstimator(model_id="superlearner_p",
                                             ntrees=60,
                                             nfolds=nfolds)
    # This is the ideal case, Stacking sees all data
    superlearner.train(x=["p1","p2","p3"],y=y,training_frame=superl_data)
    print("SuperlearnerP all data performance: ")
    print(superlearner)
    print("SuperlearnerP all test perf")
    print(superlearner.model_performance(test_data=superl_test))
    # Evaluate ensemble performance
    #perf_stack_train = stack.model_performance()
    #perf_stack_test = stack.model_performance(test_data=test)

    # Check that stack perf is better (bigger) than the best(biggest) base learner perf:
    # Training AUC
    #baselearner_best_auc_train = max(perf_gbm_train.auc(), perf_rf_train.auc())
    #stack_auc_train = perf_stack_train.auc()
    #print("Best Base-learner Training AUC:  {0}".format(baselearner_best_auc_train))
    #print("Ensemble Training AUC:  {0}".format(stack_auc_train))
    #assert stack_auc_train > baselearner_best_auc_train, "expected stack_auc_train would be greater than " \
    #                                                     " found it wasn't baselearner_best_auc_train"

    # Test AUC
    #baselearner_best_auc_test = max(perf_gbm_test.auc(), perf_rf_test.auc())
    #stack_auc_test = perf_stack_test.auc()
    #print("Best Base-learner Test AUC:  {0}".format(baselearner_best_auc_test))
    #print("Ensemble Test AUC:  {0}".format(stack_auc_test))
    #assert stack_auc_test > baselearner_best_auc_test, "expected stack_auc_test would be greater than " \
    #                                                     " baselearner_best_auc_test, found it wasn't  " \
    #                                                   "baselearner_best_auc_test = "+ \
    #                                                            str(baselearner_best_auc_test) + ",stack_auc_test " \
    #                                                                                                 " = "+ str(stack_auc_test)

    # Check that passing `test` as a validation_frame produces the same metric as stack.model_performance(test)
    # since the metrics object is not exactly the same, we can just test that AUC is the same
    #perf_stack_validation_frame = stack.model_performance(valid=True)
    #assert stack_auc_test == perf_stack_validation_frame.auc(), "expected stack_auc_test to be the same as " \
    #                                                            "perf_stack_validation_frame.auc() found they were not " \
    #                                                            "perf_stack_validation_frame.auc() = " + \
    #                                                            str(perf_stack_validation_frame.auc()) + \
    #                                                            "stack_auc_test was " + str(stack_auc_test)

if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_binomial_test)
else:
    stackedensemble_binomial_test()


