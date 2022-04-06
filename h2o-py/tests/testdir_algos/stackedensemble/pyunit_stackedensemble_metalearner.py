#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import sys
import warnings
sys.path.insert(1,"../../../")  # allow us to run this standalone

import h2o
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
        assert(se.params['metalearner_algorithm']['actual'] == expected_algo)
        if meta_params:
            assert(se.params['metalearner_nfolds']['actual'] == 3)

        meta = h2o.get_model(se.metalearner().model_id)
        assert(meta.algo == expected_algo), "Expected that the metalearner would use {}, but actually used {}.".format(expected_algo, meta.algo)
        if meta_params:
            assert(meta.params['nfolds']['actual'] == 3)

    metalearner_algos = ['AUTO', 'deeplearning', 'drf', 'gbm', 'glm', 'naivebayes', 'xgboost']
    for algo in metalearner_algos:
        expected_algo = 'glm' if algo == 'AUTO' else algo
        train_ensemble_using_metalearner(algo, expected_algo)


# PUBDEV-6955 - modify metalearner property to return metalearner object not JSON while being backward compatible
# in regards to the "name" property
def metalearner_property_test():
    train = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_test.csv"))
    x = train.columns
    y = "species"
    x.remove(y)

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True)
    gbm.train(x=x, y=y, training_frame=train)
    rf = H2ORandomForestEstimator(nfolds=nfolds,
                                  fold_assignment="Modulo",
                                  keep_cross_validation_predictions=True)
    rf.train(x=x, y=y, training_frame=train)
    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     validation_frame=test,
                                     base_models=[gbm.model_id, rf.model_id])
    se.train(x=x, y=y, training_frame=train)

    old_way_retrieved_metalearner = h2o.get_model(se.metalearner().model_id)

    # Test backward compatibility
    assert se.metalearner().model_id == old_way_retrieved_metalearner.model_id
    # Test we get the same metalearner as we used to get
    assert old_way_retrieved_metalearner.model_id == se.metalearner().model_id

    # Check we emit deprecation warning
    for v in sys.modules.values():
        if getattr(v, '__warningregistry__', None):
            v.__warningregistry__ = {}
    with warnings.catch_warnings(record=True) as ws:
        # Get all DeprecationWarnings
        warnings.simplefilter("always", DeprecationWarning)
        # Trigger a warning.
        _ = se.metalearner()["name"]
        # Assert that we get the deprecation warning
        assert any((
            issubclass(w.category, DeprecationWarning) and
            "metalearner()['name']" in str(w.message)
            for w in ws
        ))


def metalearner_parameters_test():
    train = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_test.csv"))
    x = train.columns
    y = "species"
    x.remove(y)

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True)
    gbm.train(x=x, y=y, training_frame=train)
    rf = H2ORandomForestEstimator(nfolds=nfolds,
                                  fold_assignment="Modulo",
                                  keep_cross_validation_predictions=True)
    rf.train(x=x, y=y, training_frame=train)

    se_nb = H2OStackedEnsembleEstimator(training_frame=train,
                                        validation_frame=test,
                                        base_models=[gbm.model_id, rf.model_id],
                                        metalearner_algorithm='naivebayes',
                                        metalearner_params={'min_prob': 0.5})
    se_nb.train(x=x, y=y, training_frame=train)

    assert se_nb.actual_params["metalearner_algorithm"] == "naivebayes"
    assert '{"min_prob": [0.5]}' == se_nb.actual_params["metalearner_params"]

    se_xgb = H2OStackedEnsembleEstimator(training_frame=train,
                                         validation_frame=test,
                                         base_models=[gbm.model_id, rf.model_id],
                                         metalearner_algorithm='xgboost',
                                         metalearner_params={'booster': 'dart'})
    se_xgb.train(x=x, y=y, training_frame=train)

    assert se_xgb.actual_params["metalearner_algorithm"] == "xgboost"
    assert '{"booster": ["dart"]}' == se_xgb.actual_params["metalearner_params"]


def stackensemble_delegates_to_metalearner_some_attributes_test():
    train = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    x = train.columns
    y = "CAPSULE"
    x.remove(y)

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True)
    gbm.train(x=x, y=y, training_frame=train)
    rf = H2ORandomForestEstimator(nfolds=nfolds,
                                  fold_assignment="Modulo",
                                  keep_cross_validation_predictions=True)
    rf.train(x=x, y=y, training_frame=train)
    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     validation_frame=train,
                                     base_models=[gbm.model_id, rf.model_id],
                                     metalearner_nfolds=nfolds)
    se.train(x=x, y=y, training_frame=train)
    assert (se.cross_validation_metrics_summary()._cell_values == se.metalearner().cross_validation_metrics_summary()._cell_values)


pyunit_utils.run_tests([
    stackedensemble_metalearner_test,
    metalearner_property_test,
    metalearner_parameters_test,
    stackensemble_delegates_to_metalearner_some_attributes_test,
])
