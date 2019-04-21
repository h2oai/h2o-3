#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils as pu


seed = 1


def prepare_data(blending=False):
    train = h2o.import_file(path=pu.locate("smalldata/testng/higgs_train_5k.csv"))
    test = h2o.import_file(path=pu.locate("smalldata/testng/higgs_test_5k.csv"))
    target = "response"
    for fr in [train, test]:
        fr[target] = fr[target].asfactor()
    ds = pu.ns(x=fr.columns, y=target, train=train, test=test)

    if blending:
        train, blend = train.split_frame(ratios=[.7], seed=seed)
        return ds.extend(train=train, blend=blend)
    else:
        return ds


def train_base_models(dataset, **kwargs):
    model_args = kwargs if hasattr(dataset, 'blend') else dict(nfolds=3, fold_assignment="Modulo", keep_cross_validation_predictions=True, **kwargs)

    gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                       ntrees=10,
                                       max_depth=3,
                                       min_rows=2,
                                       learn_rate=0.2,
                                       seed=seed,
                                       **model_args)
    gbm.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)

    rf = H2ORandomForestEstimator(ntrees=10,
                                  seed=seed,
                                  **model_args)
    rf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
    return [gbm, rf]


def train_stacked_ensemble(dataset, base_models, **kwargs):
    se = H2OStackedEnsembleEstimator(base_models=base_models, seed=seed)
    se.train(x=dataset.x, y=dataset.y,
             training_frame=dataset.train,
             blending_frame=dataset.blend if hasattr(dataset, 'blend') else None,
             **kwargs)
    return se


def test_suite_stackedensemble_binomial(blending=False):
    
    def test_predict_on_se_model():
        ds = prepare_data(blending)
        models = train_base_models(ds)
        se = train_stacked_ensemble(ds, models)
        pred = se.predict(test_data=ds.test)
        assert pred.nrow == ds.test.nrow, "expected " + str(pred.nrow) + " to be equal to " + str(ds.test.nrow)
        assert pred.ncol == 3, "expected " + str(pred.ncol) + " to be equal to 3 but it was equal to " + str(pred.ncol)
        
    
    def test_se_performance_is_better_than_individual_models():
        ds = prepare_data(blending)
        base_models = train_base_models(ds)
        
        def compute_perf(model):
            perf = pu.ns(
                train=model.model_performance(train=True),
                test=model.model_performance(test_data=ds.test)
            )
            print("{} training performance: ".format(model.model_id))
            print(perf.train)
            print("{} test performance: ".format(model.model_id))
            print(perf.test)
            return perf

        base_perfs = {}
        for model in base_models:
            base_perfs[model.model_id] = compute_perf(model)

        se = train_stacked_ensemble(ds, base_models)
        perf_se = compute_perf(se)

        # Check that stack perf is better (bigger) than the best(biggest) base learner perf:
        # Training AUC
        baselearner_best_auc_train = max([perf.train.auc() for perf in base_perfs.values()])
        stack_auc_train = perf_se.train.auc()
        print("Best Base-learner Training AUC:  {}".format(baselearner_best_auc_train))
        print("Ensemble Training AUC:  {}".format(stack_auc_train))
        assert stack_auc_train > baselearner_best_auc_train, \
            "expected SE training AUC would be greater than the best of base learner training AUC, but obtained: " \
            "AUC (SE) = {}, AUC (best base learner) = {}".format(stack_auc_train, baselearner_best_auc_train)

        # Test AUC
        baselearner_best_auc_test = max([perf.test.auc() for perf in base_perfs.values()])
        stack_auc_test = perf_se.test.auc()
        print("Best Base-learner Test AUC:  {}".format(baselearner_best_auc_test))
        print("Ensemble Test AUC:  {}".format(stack_auc_test))
        assert stack_auc_test > baselearner_best_auc_test, \
            "expected SE test AUC would be greater than the best of base learner test AUC, but obtained: " \
            "AUC (SE) = {}, AUC (best base learner) = {}".format(stack_auc_test, baselearner_best_auc_test)
        
    
    def test_validation_frame_produces_same_metric_as_perf_test():
        ds = prepare_data(blending)
        models = train_base_models(ds)
        se = train_stacked_ensemble(ds, models, validation_frame=ds.test)
        se_perf = se.model_performance(test_data=ds.test)
        # since the metrics object is not exactly the same, we can just test that AUC is the same
        se_perf_validation_frame = se.model_performance(valid=True)
        assert se_perf.auc() == se_perf_validation_frame.auc(), \
            "expected SE test AUC to be the same as SE validation frame AUC, but obtained: " \
            "AUC (perf on test) = {}, AUC (test passed as validation frame) = {}".format(se_perf.auc(), se_perf_validation_frame.auc())
        
    return [pu.tag_test(test, 'blending' if blending else None) for test in [
        test_predict_on_se_model,
        test_se_performance_is_better_than_individual_models,
        test_validation_frame_produces_same_metric_as_perf_test
    ]]


pu.run_tests([
    test_suite_stackedensemble_binomial(),
    test_suite_stackedensemble_binomial(blending=True)
])
