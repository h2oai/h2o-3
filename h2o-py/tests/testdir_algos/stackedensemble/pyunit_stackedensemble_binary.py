#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import tempfile
import shutil
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

    def train_stacked_ensemble(self, dataset, base_models):
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], seed=seed)
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
        se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], seed=seed)
        se.train(x=dataset.x, y=dataset.y, training_frame=dataset.train, blending_frame=dataset.blend)
        return se


def test_suite_stackedensemble_binary_model(blending=False):
    t = StackedEnsembleTest() if not blending else StackedEnsembleBlendingTest()

    def test_saved_binary_model_produces_same_predictions_as_original():
        ds = t.prepare_data()
        base_models = t.train_base_models(ds)
        se_model = t.train_stacked_ensemble(ds, base_models)
        
        #Predict in ensemble in Py client
        preds_py = se_model.predict(ds.test)
        
        tmp_dir = tempfile.mkdtemp()
        try:
            bin_file = h2o.save_model(se_model, tmp_dir)
            #Load binary model and predict
            bin_model = h2o.load_model(pu.locate(bin_file))
            preds_bin = bin_model.predict(ds.test)
        finally:
            shutil.rmtree(tmp_dir)

        #Predictions from model in Py and binary model should be the same
        pred_diff = preds_bin - preds_py
        assert pred_diff["p0"].max() < 1e-11
        assert pred_diff["p1"].max() < 1e-11
        assert pred_diff["p0"].min() > -1e-11
        assert pred_diff["p1"].min() > -1e-11
    
    return [
        test_saved_binary_model_produces_same_predictions_as_original
    ]
    

pu.run_tests([
    test_suite_stackedensemble_binary_model(),
    test_suite_stackedensemble_binary_model(blending=True),
])
