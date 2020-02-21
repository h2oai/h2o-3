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


    def train_ensemble_using_metalearner(algo, expected_algo):
        print("Training ensemble using {} metalearner.".format(algo))

        meta_params = dict(metalearner_nfolds=3)

        se = H2OStackedEnsembleEstimator(base_models=[my_gbm, my_rf], metalearner_algorithm=algo, **meta_params)
        se.train(x=x, y=y, training_frame=train)
        assert(se.params['metalearner_algorithm']['actual'] == algo)
        if meta_params:
            assert(se.params['metalearner_nfolds']['actual'] == 3)

        meta = h2o.get_model(se.metalearner()['name'])
        assert(meta.algo == expected_algo), "Expected that the metalearner would use {}, but actually used {}.".format(expected_algo, meta.algo)
        if meta_params:
            assert(meta.params['nfolds']['actual'] == 3)

    metalearner_algos = ['AUTO', 'deeplearning', 'drf', 'gbm', 'glm', 'naivebayes', 'xgboost']
    for algo in metalearner_algos:
        expected_algo = 'glm' if algo == 'AUTO' else algo
        train_ensemble_using_metalearner(algo, expected_algo)



if __name__ == "__main__":
    pyunit_utils.standalone_test(stackedensemble_metalearner_test)
else:
    stackedensemble_metalearner_test()
