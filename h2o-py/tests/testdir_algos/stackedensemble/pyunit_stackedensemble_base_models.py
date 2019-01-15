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
        fr = h2o.import_file(path=pu.locate("smalldata/testng/higgs_train_5k.csv"),
                             destination_frame="higgs_train_5k")
        target = "response"
        fr[target] = fr[target].asfactor()
        return pu.ns(x=fr.columns, y=target, train=fr)

    def train_base_models(self, dataset):
        nfolds = 3
        gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                           ntrees=10,
                                           nfolds=nfolds,
                                           fold_assignment="Modulo",
                                           keep_cross_validation_predictions=True,
                                           seed=seed)
        gbm.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)

        rf = H2ORandomForestEstimator(ntrees=10,
                                      nfolds=nfolds,
                                      fold_assignment="Modulo",
                                      keep_cross_validation_predictions=True,
                                      seed=seed)
        rf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        return [gbm, rf]

    def train_stacked_ensemble(self, dataset, base_models):
        se = H2OStackedEnsembleEstimator(base_models=base_models, seed=seed)
        se.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
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

    def train_stacked_ensemble(self, dataset, base_models):
        se = H2OStackedEnsembleEstimator(base_models=base_models, seed=seed)
        se.train(x=dataset.x, y=dataset.y, training_frame=dataset.train, blending_frame=dataset.blend)
        return se
    

def test_suite_stackedensemble_base_models(blending=False):
    t = StackedEnsembleTest() if not blending else StackedEnsembleBlendingTest()

    def test_base_models_can_be_passed_as_objects_or_as_ids():
        """This test checks the following:
        1) That passing in a list of models for base_models works.
        2) That passing in a list of models and model_ids results in the same stacked ensemble.
        """
        ds = t.prepare_data()
        base_models = t.train_base_models(ds)
        se1 = t.train_stacked_ensemble(ds, [m.model_id for m in base_models])
        se2 = t.train_stacked_ensemble(ds, base_models)

        # Eval train AUC to assess equivalence
        assert se1.auc() == se2.auc()
        
    return [
        test_base_models_can_be_passed_as_objects_or_as_ids
    ]


pu.run_tests([
    test_suite_stackedensemble_base_models(),
    test_suite_stackedensemble_base_models(blending=True),
])
