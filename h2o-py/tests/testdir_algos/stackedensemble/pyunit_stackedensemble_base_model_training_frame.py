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
    
    def train_base_models(self, datasets):
        nfolds = 3
        gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                           ntrees=10,
                                           nfolds=nfolds,
                                           fold_assignment="Modulo",
                                           keep_cross_validation_predictions=True,
                                           seed=seed)
        gbm.train(x=datasets.gbm.x, y=datasets.gbm.y, training_frame=datasets.gbm.train)

        rf = H2ORandomForestEstimator(ntrees=10,
                                      nfolds=nfolds,
                                      fold_assignment="Modulo",
                                      keep_cross_validation_predictions=True,
                                      seed=seed)
        rf.train(x=datasets.drf.x, y=datasets.drf.y, training_frame=datasets.drf.train)
        return [gbm, rf]

    def train_stacked_ensemble(self, dataset, base_models):
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], seed=seed)
        se.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        return se
        
        
class StackedEnsembleBlendingTest(StackedEnsembleTest):
    
    def prepare_data(self):
        ds = super(self.__class__, self).prepare_data()
        train, blend = ds.train.split_frame(ratios=[.7], seed=seed)
        return ds.extend(train=train, blend=blend)
    
    def train_base_models(self, datasets):
        gbm = H2OGradientBoostingEstimator(distribution="bernoulli",
                                           ntrees=10,
                                           seed=seed)
        gbm.train(x=datasets.gbm.x, y=datasets.gbm.y, training_frame=datasets.gbm.train)

        rf = H2ORandomForestEstimator(ntrees=10,
                                      seed=seed)
        rf.train(x=datasets.drf.x, y=datasets.drf.y, training_frame=datasets.drf.train)
        return [gbm, rf]

    def train_stacked_ensemble(self, dataset, base_models):
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], seed=seed)
        se.train(x=dataset.x, y=dataset.y, training_frame=dataset.train, blending_frame=dataset.blend)
        return se


def test_suite_stackedensemble_training_frame(blending=False):
    t = StackedEnsembleTest() if not blending else StackedEnsembleBlendingTest()
    
    def test_base_models_can_use_different_x():
        """
        test that passing in base models that use different subsets of 
        the features works. (different x, but same training_frame)
        """
        ds = t.prepare_data()
        datasets = pu.ns(gbm=ds.extend(x=ds.x[1:11]), 
                         drf=ds.extend(x=ds.x[13:20]))
        
        bm = t.train_base_models(datasets)
        se = t.train_stacked_ensemble(ds, bm)
        ds.x = None
        se_nox = t.train_stacked_ensemble(ds, bm)
        assert se.auc() > 0
        assert se.auc() == se_nox.auc()
        
    
    def test_base_models_can_use_different_compatible_training_frames():
        """
        test that passing in base models that use different subsets of 
        the features works. (different training_frame) 
        """
        ds = t.prepare_data()
        datasets = pu.ns(gbm=ds.extend(x=None, 
                                       train=ds.train[list(range(1, 11))].cbind(ds.train[ds.y])), 
                         drf=ds.extend(x=None,
                                       train=ds.train[list(range(13, 20))].cbind(ds.train[ds.y])))
        bm = t.train_base_models(datasets)
        se = t.train_stacked_ensemble(ds, bm)
        assert se.auc() > 0
        
    def test_se_fails_when_base_models_use_incompatible_training_frames():
        """
        test that SE fails when passing in base models that were trained with frames of different size 
        """
        ds = t.prepare_data()
        datasets = pu.ns(gbm=ds.extend(x=None),
                         drf=ds.extend(x=None, train=ds.train[0:ds.train.nrows//2,:]))
        bm = t.train_base_models(datasets)
        try:
            t.train_stacked_ensemble(ds, bm)
            assert False, "Stacked Ensembles of models with different training frame sizes should fail"
        except Exception as e:
            assert "Base models are inconsistent: they use different size (number of rows) training frames" in str(e), "wrong error message: {}".format(str(e))
            # raise e
    
    return [pu.tag_test(test, 'blending' if blending else None) for test in [
        test_base_models_can_use_different_x,
        test_base_models_can_use_different_compatible_training_frames,
        test_se_fails_when_base_models_use_incompatible_training_frames
    ]]


pu.run_tests([
    test_suite_stackedensemble_training_frame(),
    test_suite_stackedensemble_training_frame(blending=True),
])
