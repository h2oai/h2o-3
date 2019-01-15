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
        train = h2o.import_file(path=pu.locate("smalldata/iris/iris_train.csv"))
        target = "species"
        train[target] = train[target].asfactor()
        return pu.ns(x=train.columns, y=target, train=train)

    def train_base_models(self, dataset):
        nfolds = 3
        gbm = H2OGradientBoostingEstimator(distribution="multinomial",
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

    def train_stacked_ensemble(self, dataset, base_models, keep_levelone_frame=False):
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], 
                                         keep_levelone_frame=keep_levelone_frame,
                                         seed=seed)
        se.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        return se


class StackedEnsembleBlendingTest(StackedEnsembleTest):

    def prepare_data(self):
        ds = super(self.__class__, self).prepare_data()
        train, blend = ds.train.split_frame(ratios=[.7], seed=seed)
        return ds.extend(train=train, blend=blend)

    def train_base_models(self, dataset):
        gbm = H2OGradientBoostingEstimator(distribution="multinomial",
                                           ntrees=10,
                                           seed=seed)
        gbm.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)

        rf = H2ORandomForestEstimator(ntrees=10,
                                      seed=seed)
        rf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
        return [gbm, rf]

    def train_stacked_ensemble(self, dataset, base_models, keep_levelone_frame=False):
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], 
                                         keep_levelone_frame=keep_levelone_frame,
                                         seed=seed)
        se.train(x=dataset.x, y=dataset.y,
                 training_frame=dataset.train,
                 blending_frame=dataset.blend)
        return se


def test_suite_stackedensemble_levelone_frame(blending=False):
    t = StackedEnsembleTest() if not blending else StackedEnsembleBlendingTest()

    def test_levelone_frame_not_accessible_with__keep_levelone_frame__False():
        ds = t.prepare_data()
        models = t.train_base_models(ds)
        se = t.train_stacked_ensemble(ds, models)
        assert se.levelone_frame_id() is None, \
            "Level one frame should not be available when keep_levelone_frame is False."
    
    def test_levelone_frame_accessible_with__keep_levelone_frame__True():
        ds = t.prepare_data()
        models = t.train_base_models(ds)
        se = t.train_stacked_ensemble(ds, models, keep_levelone_frame=True)
        assert se.levelone_frame_id() is not None, \
            "Level one frame should be available when keep_levelone_frame is True."
    
    def test_levelone_frame_has_expected_dimensions():
        ds = t.prepare_data()
        models = t.train_base_models(ds)
        se = t.train_stacked_ensemble(ds, models, keep_levelone_frame=True)
        level_one_frame = h2o.get_frame(se.levelone_frame_id()["name"])
        
        se_training_frame = ds.blend if blending else ds.train
        
        num_col_level_one_frame = (se_training_frame[ds.y].unique().nrow) * len(models) + 1  # count_classes(probabilities) * count_models + 1 (target)
        assert level_one_frame.ncols == num_col_level_one_frame, \
            "The number of columns in a level one frame should be numClasses * numBaseModels + 1."
        assert level_one_frame.nrows == se_training_frame.nrows, \
            "The number of rows in the level one frame should match train number of rows. "
    
    return [pu.tag_test(test, 'blending' if blending else None) for test in [
        test_levelone_frame_not_accessible_with__keep_levelone_frame__False,
        test_levelone_frame_accessible_with__keep_levelone_frame__True,
        test_levelone_frame_has_expected_dimensions
    ]]


pu.run_tests([
    test_suite_stackedensemble_levelone_frame(),
    test_suite_stackedensemble_levelone_frame(blending=True),
])


