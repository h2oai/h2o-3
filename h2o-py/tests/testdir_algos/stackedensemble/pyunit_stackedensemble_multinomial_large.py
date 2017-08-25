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


def stackedensemble_multinomial_test():
    """This test check the following (for multinomial regression):
    1) That H2OStackedEnsembleEstimator executes w/o errors on a 3-model manually constructed ensemble.
    2) That .predict() works on a stack.
    3) That .model_performance() works on a stack.
    4) That test performance is better on ensemble vs the base learners.
    5) That the validation_frame arg on H2OStackedEnsembleEstimator works correctly.
    """

    df = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/mnist/test.csv.gz"))
    y = "C785"
    x = list(range(784))
    df[y] = df[y].asfactor()
    train = df[0:5000,:]
    test = df[5000:10000,:]
    # Number of CV folds (to generate level-one data for stacking)
    nfolds = 2


    # train and cross-validate a GBM
    my_gbm = H2OGradientBoostingEstimator(distribution="multinomial",
                                          nfolds=nfolds,
                                          ntrees=10,
                                          fold_assignment="Modulo",
                                          keep_cross_validation_predictions=True,
                                          seed=1)
    my_gbm.train(x=x, y=y, training_frame=train)

    # evaluate the performance
    perf_gbm_train = my_gbm.model_performance()
    perf_gbm_test = my_gbm.model_performance(test_data=test)
    print("GBM training performance: ")
    print(perf_gbm_train)
    print("GBM test performance: ")
    print(perf_gbm_test)

    # train and cross-validate a RF
    my_rf = H2ORandomForestEstimator(ntrees=10,
                                     nfolds=nfolds,
                                     fold_assignment="Modulo",
                                     keep_cross_validation_predictions=True,
                                     seed=1)

    my_rf.train(x=x, y=y, training_frame=train)

    # evaluate performance
    perf_rf_train = my_rf.model_performance()
    perf_rf_test = my_rf.model_performance(test_data=test)
    print("RF training performance: ")
    print(perf_rf_train)
    print("RF test performance: ")
    print(perf_rf_test)

    # Train and cross-validate an extremely-randomized RF
    my_xrf = H2ORandomForestEstimator(ntrees=10,
                                      nfolds=nfolds,
                                      histogram_type="Random",
                                      fold_assignment="Modulo",
                                      keep_cross_validation_predictions=True,
                                      seed=1)

    my_xrf.train(x=x, y=y, training_frame=train)

    # evaluate performance
    perf_xrf_train = my_xrf.model_performance()
    perf_xrf_test = my_xrf.model_performance(test_data=test)
    print("XRF training performance: ")
    print(perf_xrf_train)
    print("XRF test performance: ")
    print(perf_xrf_test)

    # Train a stacked ensemble using the GBM and GLM above
    stack = H2OStackedEnsembleEstimator(base_models=[my_gbm.model_id,  my_rf.model_id, my_xrf.model_id])
    stack.train(x=x, y=y, training_frame=train, validation_frame=test)  # also test that validation_frame is working
    assert type(stack) == h2o.estimators.stackedensemble.H2OStackedEnsembleEstimator
    assert stack.type == "classifier"

    # Check that prediction works
    pred = stack.predict(test_data=test)
    print(pred)
    assert pred.nrow == test.nrow, "expected " + str(pred.nrow) + " to be equal to " + str(test.nrow)
    assert pred.ncol == 11, "expected " + str(pred.ncol) + " to be equal to 1 but it was equal to " + str(pred.ncol)

    # Evaluate ensemble performance
    perf_stack_train = stack.model_performance()
    assert type(perf_stack_train) == h2o.model.metrics_base.H2OMultinomialModelMetrics
    perf_stack_valid = stack.model_performance(valid=True)
    assert type(perf_stack_valid) == h2o.model.metrics_base.H2OMultinomialModelMetrics
    perf_stack_test = stack.model_performance(test_data=test)
    assert type(perf_stack_test) == h2o.model.metrics_base.H2OMultinomialModelMetrics


    # Check that stack perf is better (smaller) than the best (smaller) base learner perf:
    # Test Mean Per Class Error for each base learner
    baselearner_best_mean_per_class_error_test = min(perf_gbm_test.mean_per_class_error(), \
                                                     perf_rf_test.mean_per_class_error(), \
                                                     perf_xrf_test.mean_per_class_error())
    stack_mean_per_class_error_test = perf_stack_test.mean_per_class_error()
    print("Best Base-learner Test Mean Per Class Error:  {0}".format(baselearner_best_mean_per_class_error_test))
    print("Ensemble Test Mean Per Class Error:  {0}".format(stack_mean_per_class_error_test))
    assert stack_mean_per_class_error_test <= baselearner_best_mean_per_class_error_test, + \
                                                         "expected stack_mean_per_class_error_test would be less than " \
                                                         " baselearner_best_mean_per_class_error_test, found it wasn't  " \
                                                         "baselearner_best_mean_per_class_error_test = "+ \
                                                         str(baselearner_best_mean_per_class_error_test) + \
                                                         ",stack_mean_per_class_error_test = "+ \
                                                         str(stack_mean_per_class_error_test)

    # Check that passing `test` as a validation_frame produces the same metric as stack.model_performance(test)
    # since the metrics object is not exactly the same, we can just test that RSME is the same
    perf_stack_validation_frame = stack.model_performance(valid=True)
    assert stack_mean_per_class_error_test == perf_stack_validation_frame.mean_per_class_error(), \
                                                                  "expected stack_mean_per_class_error_test to be the same as " \
                                                                  "perf_stack_validation_frame.mean_per_class_error() found it wasn't" \
                                                                  "perf_stack_validation_frame.mean_per_class_error() = " + \
                                                                  str(perf_stack_validation_frame.mean_per_class_error()) + \
                                                                  "stack_mean_per_class_error_test was " + \
                                                                  str(stack_mean_per_class_error_test)

if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_multinomial_test)
else:
    stackedensemble_multinomial_test()


