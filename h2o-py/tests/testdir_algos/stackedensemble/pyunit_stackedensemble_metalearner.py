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


def stackedensemble_metalearner_test():
    """This test checks the following:
    1) That H2OStackedEnsembleEstimator `metalearner_nfolds` works correctly
    2) That H2OStackedEnsembleEstimator `metalearner_nfolds` works in concert with `metalearner_nfolds`
    """

    # Import training set
    train = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_train_5k.csv"),
                            destination_frame="higgs_train_5k")
    test = h2o.import_file(path=pyunit_utils.locate("smalldata/testng/higgs_test_5k.csv"),
                            destination_frame="higgs_test_5k")

    # Identify predictors and response
    x = train.columns
    y = "response"
    x.remove(y)

    # Convert response to a factor
    train[y] = train[y].asfactor()
    test[y] = test[y].asfactor()

    # Set number of folds for base learners
    nfolds = 3

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

    
    # Check that not setting metalearner_algorithm still produces correct results
    # should be glm with non-negative weights
    stack0 = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf])
    stack0.train(x=x, y=y, training_frame=train)
    # Check that metalearner_algorithm default is GLM w/ non-negative
    assert(stack0.params['metalearner_algorithm']['actual'] == "glm")
    # Check that the metalearner is GLM w/ non-negative
    meta0 = h2o.get_model(stack0.metalearner()['name'])
    assert(meta0.algo == "glm")
    assert(meta0.params['non_negative']['actual'] is True)


    # Train a stacked ensemble & check that metalearner_algorithm works
    stack1 = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_algorithm="gbm")
    stack1.train(x=x, y=y, training_frame=train)
    # Check that metalearner_algorithm is a default GBM
    assert(stack1.params['metalearner_algorithm']['actual'] == "gbm")
    # Check that the metalearner is default GBM
    meta1 = h2o.get_model(stack1.metalearner()['name'])
    assert(meta1.algo == "gbm")
    # TO DO: Add a check that no other hyperparams have been set


    # Train a stacked ensemble & check that metalearner_algorithm works with CV    
    stack2 = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_algorithm="drf", metalearner_nfolds=3)
    stack2.train(x=x, y=y, training_frame=train)
    # Check that metalearner_algorithm is a default RF
    assert(stack2.params['metalearner_algorithm']['actual'] == "drf")
    # Check that CV was performed
    assert(stack2.params['metalearner_nfolds']['actual'] == 3)
    meta2 = h2o.get_model(stack2.metalearner()['name'])
    assert(meta2.algo == "drf")
    assert(meta2.params['nfolds']['actual'] == 3)



if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_metalearner_test)
else:
    stackedensemble_metalearner_test()