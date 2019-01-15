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

class StackedEnsembleTest(object):

    def prepare_data(self):
        train = h2o.import_file(path=pu.locate("smalldata/testng/higgs_train_5k.csv"))
        test = h2o.import_file(path=pu.locate("smalldata/testng/higgs_test_5k.csv"))
        target = "response"
        for fr in [train, test]:
            fr[target] = fr[target].asfactor()
        return pu.ns(x=train.columns, y=target, train=train, test=test)

    def train_base_models(self, dataset):
        nfolds = 3
        gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                           ntrees=10,
                                           max_depth=3,
                                           min_rows=2,
                                           learn_rate=0.2,
                                           nfolds=nfolds,
                                           fold_assignment="Modulo",
                                           keep_cross_validation_predictions=True,
                                           seed=seed)
        gbm.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)

        rf = H2ORandomForestEstimator(ntrees=20,
                                      nfolds=nfolds,
                                      fold_assignment="Modulo",
                                      keep_cross_validation_predictions=True,
                                      seed=seed)
        rf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        return [gbm, rf]

    def train_stacked_ensemble(self, dataset, base_models, valid=False):
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], seed=seed)
        se.train(x=dataset.x, y=dataset.y, 
                 training_frame=dataset.train, 
                 validation_frame=dataset.test if valid else None)
        return se


class StackedEnsembleBlendingTest(StackedEnsembleTest):

    def prepare_data(self):
        ds = super(self.__class__, self).prepare_data()
        train, blend = ds.train.split_frame(ratios=[.7], seed=seed)
        return ds.extend(train=train, blend=blend)

    def train_base_models(self, dataset):
        gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                           ntrees=10,
                                           seed=seed)
        gbm.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)

        rf = H2ORandomForestEstimator(ntrees=10,
                                      seed=seed)
        rf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        return [gbm, rf]

    def train_stacked_ensemble(self, dataset, base_models, valid=False):
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], seed=seed)
        se.train(x=dataset.x, y=dataset.y, 
                 training_frame=dataset.train, 
                 validation_frame=dataset.test if valid else None,
                 blending_frame=dataset.blend)
        return se


def test_suite_stackedensemble_binomial(blending=False):
    t = StackedEnsembleTest() if not blending else StackedEnsembleBlendingTest()
    
    def test_predict_on_se_model():
        ds = t.prepare_data()
        models = t.train_base_models(ds)
        se = t.train_stacked_ensemble(ds, models)
        pred = se.predict(test_data=ds.test)
        assert pred.nrow == ds.test.nrow, "expected " + str(pred.nrow) + " to be equal to " + str(ds.test.nrow)
        assert pred.ncol == 3, "expected " + str(pred.ncol) + " to be equal to 3 but it was equal to " + str(pred.ncol)
        
    
    def test_se_performance_is_better_than_individual_models():
        ds = t.prepare_data()
        base_models = t.train_base_models(ds)
        
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

        se = t.train_stacked_ensemble(ds, base_models)
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
        ds = t.prepare_data()
        models = t.train_base_models(ds)
        se = t.train_stacked_ensemble(ds, models, valid=True)
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
