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
        train, valid = train.split_frame(ratios=[.8], seed=seed)
        return pu.ns(x=train.columns, y=target, train=train, valid=valid, test=test)

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
                 validation_frame=dataset.valid if valid else None)
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

        rf = H2ORandomForestEstimator(ntrees=20,
                                      seed=seed)
        rf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        return [gbm, rf]

    def train_stacked_ensemble(self, dataset, base_models, valid=False):
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], seed=seed)
        se.train(x=dataset.x, y=dataset.y,
                 training_frame=dataset.train,
                 validation_frame=dataset.valid if valid else None,
                 blending_frame=dataset.blend)
        return se


def test_suite_stackedensemble_validation_frame(blending=False):
    t = StackedEnsembleTest() if not blending else StackedEnsembleBlendingTest()
    
    def test_validation_metrics_are_computed_when_providing_validation_frame():
        ds = t.prepare_data()
        base_models = t.train_base_models(ds)
        se_valid = t.train_stacked_ensemble(ds, base_models, valid=True)
        
        assert se_valid.model_performance(valid=True) is not None
        assert type(se_valid.model_performance(valid=True)) == h2o.model.metrics_base.H2OBinomialModelMetrics
        assert type(se_valid.auc(valid=True)) == float
                    
        
    def test_a_better_model_is_produced_with_validation_frame():
        ds = t.prepare_data()
        base_models = t.train_base_models(ds)
        se_no_valid = t.train_stacked_ensemble(ds, base_models, valid=False)
        se_valid = t.train_stacked_ensemble(ds, base_models, valid=True)

        assert se_no_valid.model_performance(valid=True) is None
        assert se_valid.model_performance(valid=True) is not None
        
        se_no_valid_perf = se_no_valid.model_performance(test_data=ds.test)
        se_valid_perf = se_valid.model_performance(test_data=ds.test)
        tolerance = 1e-3  # ad hoc tolerance as there's no guarantee perf will actually be better with validation frame 
        assert se_no_valid_perf.auc() < se_valid_perf.auc() or (se_no_valid_perf.auc() - se_valid_perf.auc()) < tolerance, \
            "Expected that a better model would be produced when passing a validation frame, bot obtained: " \
            "AUC (no validation) = {}, AUC (validation frame) = {}".format(se_no_valid_perf.auc(), se_valid_perf.auc())
        
    
    return [pu.tag_test(test, 'blending' if blending else None) for test in [
        test_validation_metrics_are_computed_when_providing_validation_frame,
        test_a_better_model_is_produced_with_validation_frame
    ]]
    
    
pu.run_tests([
    test_suite_stackedensemble_validation_frame(),
    test_suite_stackedensemble_validation_frame(blending=True)
])

