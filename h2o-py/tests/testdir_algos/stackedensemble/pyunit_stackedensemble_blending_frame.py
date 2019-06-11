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


def prepare_data(blending=True):
    train = h2o.import_file(path=pu.locate("smalldata/testng/higgs_train_5k.csv"))
    test = h2o.import_file(path=pu.locate("smalldata/testng/higgs_test_5k.csv"))
    target = "response"
    for fr in [train, test]:
        fr[target] = fr[target].asfactor()
    ds = pu.ns(x=train.columns, y=target, train=train, test=test)
    
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

    rf = H2ORandomForestEstimator(ntrees=20,
                                  seed=seed,
                                  **model_args)
    rf.train(x=dataset.x, y=dataset.y, training_frame=dataset.train)
    return [gbm, rf]


def train_stacked_ensemble(dataset, base_models, **kwargs):
    se = H2OStackedEnsembleEstimator(base_models=[m.model_id for m in base_models], seed=seed)
    se.train(x=dataset.x, y=dataset.y,
             training_frame=dataset.train,
             blending_frame=dataset.blend if hasattr(dataset, 'blend') else None,
             **kwargs)
    return se


def test_passing_blending_frame_triggers_blending_mode():
    ds = prepare_data(blending=True)
    base_models = train_base_models(ds)
    se = train_stacked_ensemble(ds, base_models)
    assert se.stacking_strategy() == 'blending'
    
    
def test_blending_mode_usually_performs_worse_than_CV_stacking_mode():
    perfs = {}
    for blending in [True, False]:
        ds = prepare_data(blending=blending)
        base_models = train_base_models(ds)
        se_model = train_stacked_ensemble(ds, base_models)
        perf = se_model.model_performance(test_data=ds.test)
        perfs[se_model.stacking_strategy()] = perf
        
    # this performance difference is not guaranteed, but usually expected
    assert perfs['blending'].auc() < perfs['cross_validation'].auc(), \
        "SE blending should perform worse than CV stacking, but obtained: " \
        "AUC (blending) = {}, AUC (CV stacking) = {}".format(perfs['blending'].auc(), perfs['cross_validation'].auc())


def test_training_frame_is_still_required_in_blending_mode():
    ds = prepare_data(blending=True)
    base_models = train_base_models(ds)
    try:
        train_stacked_ensemble(ds.extend(train=None), base_models)
        assert False, "StackedEnsemble training without training_frame should have raised an exception"
    except Exception as e:
        assert "Training frame required for stackedensemble algorithm" in str(e), "Wrong error message {}".format(str(e))


pu.run_tests([
    test_passing_blending_frame_triggers_blending_mode,
    test_blending_mode_usually_performs_worse_than_CV_stacking_mode,
    test_training_frame_is_still_required_in_blending_mode
])
