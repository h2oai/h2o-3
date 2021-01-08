#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

import numpy as np

import h2o
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid import H2OGridSearch
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils as pu

seed = 1


def test_models_not_predicting_some_classes_dont_corrupt_resulting_SE_model():

    def unique(fr):
        return np.unique(fr.as_data_frame().values).tolist()

    def scores_and_preds(models, test):
        retval = lambda d: d
        if not isinstance(models, list):
            models = [models]
            retval = lambda d: next(iter(d.values()))
        training_scores = {m.key: m.mean_per_class_error() for m in models}
        cv_scores = {m.key: m.mean_per_class_error(xval=True) for m in models}
        test_scores = {m.key: m.model_performance(test).mean_per_class_error() for m in models}
        test_predictions = {m.key: m.predict(test) for m in models}
        test_pclasses = {m.key: unique(test_predictions[m.key]['predict']) for m in models}
        return pu.ns(
            training_scores=retval(training_scores),
            cv_scores=retval(cv_scores),
            test_scores=retval(test_scores),
            test_pclasses=retval(test_pclasses),
        )

    def setup_data():
        # MNIST is multinomial classification problem
        train_full = h2o.import_file(pu.locate("bigdata/laptop/mnist/train.csv.gz"))
        # test_full = h2o.import_file(pu.locate("bigdata/laptop/mnist/test.csv.gz"))
        # train = train_full
        # test = test_full
        train, test, _ = train_full.split_frame(ratios=[.05, .1], seed=seed)
        x = train.columns[:-1]
        y = -1
        for fr in [train]:
            fr[y] = fr[y].asfactor()
        domain = unique(train[y])
        print(domain)
        return pu.ns(x=x, y=y, train=train, test=test, domain=domain)

    def train_base_models(data):
        grid = H2OGridSearch(H2OGradientBoostingEstimator,
                             search_criteria=dict(
                                 strategy='RandomDiscrete',
                                 max_models=5,
                                 seed=seed,
                             ),
                             hyper_params=dict(
                                 learn_rate=[0.5, 0.8, 1.0],
                                 max_depth=[2, 3, 4, 5],
                                 ntrees=[5, 10, 15],
                             ),
                             )
        grid.train(data.x, data.y, data.train,
                   nfolds=5,
                   fold_assignment='Modulo',
                   keep_cross_validation_predictions=True)
        return grid.models

    def train_bad_model(data):
        glm = H2OGeneralizedLinearEstimator(family='multinomial',
                                            missing_values_handling='MeanImputation',
                                            alpha=[0.0, 0.2, 0.4, 0.6, 0.8, 1.0],
                                            lambda_search=True,
                                            nfolds=5,
                                            fold_assignment='Modulo',
                                            keep_cross_validation_predictions=True,
                                            seed=seed)
        glm.train(data.x, data.y, data.train, max_runtime_secs=2)
        return glm

    def check_stackedensemble_with_AUTO_metalearner(data, models):
        se = H2OStackedEnsembleEstimator(base_models=models,
                                         metalearner_nfolds=5,
                                         seed=seed)
        se.train(data.x, data.y, data.train)
        results = scores_and_preds(se, data.test)
        print(results)
        assert data.domain == results.test_pclasses, "expected predicted classes {} but got {}".format(data.domain, results.test_pclasses)

    def check_stackedensemble_with_DRF_metalearner(data, models):
        se = H2OStackedEnsembleEstimator(base_models=models,
                                         metalearner_algorithm='DRF',
                                         metalearner_nfolds=5,
                                         seed=seed)
        se.train(data.x, data.y, data.train)
        results = scores_and_preds(se, data.test)
        print(results)
        assert data.domain == results.test_pclasses, "expected predicted classes {} but got {}".format(data.domain, results.test_pclasses)

    def check_stackedensemble_with_GLM_metalearner(data, models):
        se = H2OStackedEnsembleEstimator(base_models=models,
                                         metalearner_algorithm='GLM',
                                         metalearner_nfolds=5,
                                         seed=seed)
        se.train(data.x, data.y, data.train)
        results = scores_and_preds(se, data.test)
        print(results)
        assert data.domain == results.test_pclasses, "expected predicted classes {} but got {}".format(data.domain, results.test_pclasses)

    def check_stackedensemble_with_GLM_metalearner_with_standardization_disabled(data, models):
        se = H2OStackedEnsembleEstimator(base_models=models,
                                         metalearner_algorithm='GLM',
                                         metalearner_nfolds=5,
                                         metalearner_params=dict(standardize=False),
                                         seed=seed)
        se.train(data.x, data.y, data.train)
        results = scores_and_preds(se, data.test)
        print(results)
        assert data.domain == results.test_pclasses, "expected predicted classes {} but got {}".format(data.domain, results.test_pclasses)

    data = setup_data()
    base_models = train_base_models(data)
    bad_model = train_bad_model(data)
    # print(scores_and_preds(bad_model, data.test))
    all_models = base_models + [bad_model]
    check_stackedensemble_with_AUTO_metalearner(data, all_models)
    check_stackedensemble_with_DRF_metalearner(data, all_models)
    check_stackedensemble_with_GLM_metalearner(data, all_models)
    check_stackedensemble_with_GLM_metalearner_with_standardization_disabled(data, all_models)


pu.run_tests([
    test_models_not_predicting_some_classes_dont_corrupt_resulting_SE_model
])
