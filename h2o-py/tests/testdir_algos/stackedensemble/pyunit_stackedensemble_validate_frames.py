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


def import_dataset(seed=0):
    df = h2o.import_file(path=pu.locate("smalldata/prostate/prostate.csv"))
    target = "CAPSULE"
    df[target] = df[target].asfactor()
    fr = df.split_frame(ratios=[.8,.1], seed=seed)
    return dict(train=fr[0], valid=fr[1], blend=fr[2], target=target)


def test_frames_can_be_passed_as_key():
    ds = import_dataset()

    gbm = H2OGradientBoostingEstimator(ntrees=10, nfolds=0, seed=seed)
    gbm.train(y=ds['target'], training_frame=ds['train'].frame_id, validation_frame=ds['valid'].frame_id)

    rf = H2ORandomForestEstimator(ntrees=10, nfolds=0, seed=seed,
                                  training_frame=ds['train'].frame_id, validation_frame=ds['valid'].frame_id)
    rf.train(y=ds['target'])

    se = H2OStackedEnsembleEstimator(base_models=[gbm, rf], seed=seed)
    se.train(y=ds['target'],
             training_frame=ds['train'].frame_id,
             validation_frame=ds['valid'].frame_id,
             blending_frame=ds['blend'].frame_id)

    assert se.auc() > 0


def test_validates_invalid_keys():
    ds = import_dataset()

    kw_args = [
        dict(training_frame='dummy'),
        dict(training_frame=ds['train'], validation_frame='dummy'),
        dict(training_frame=ds['train'], blending_frame='dummy'),
    ]

    # Constructor validation
    for kwargs in kw_args:
        try:
            H2OStackedEnsembleEstimator(base_models=[], **kwargs)
            raise AssertionError("should have thrown due to wrong frame key")
        except ValueError as e:
            attr = next(k for k, v in kwargs.items() if isinstance(v, str) and v == 'dummy')
            assert "'{}' must be a valid H2OFrame or key".format(attr) in str(e), str(e)


    # train method validation
    se = H2OStackedEnsembleEstimator(base_models=[])

    for kwargs in kw_args:
        try:
            se.train(y=ds['target'], **kwargs)
            raise AssertionError("should have thrown due to wrong frame key")
        except ValueError as e:
            attr = next(k for k, v in kwargs.items() if isinstance(v, str) and v == 'dummy')
            assert "'{}' must be a valid H2OFrame or key".format(attr) in str(e), str(e)


pu.run_tests([
    test_frames_can_be_passed_as_key,
    test_validates_invalid_keys
])
