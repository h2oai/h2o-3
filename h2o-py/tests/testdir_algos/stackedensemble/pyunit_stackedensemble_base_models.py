#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from h2o import get_model
from tests import pyunit_utils as pu


seed = 1


def prepare_data(blending=False):
    fr = h2o.import_file(path=pu.locate("smalldata/testng/higgs_train_5k.csv"))
    target = "response"
    fr[target] = fr[target].asfactor()
    ds = pu.ns(x=fr.columns, y=target, train=fr)

    if blending:
        train, blend = fr.split_frame(ratios=[.7], seed=seed)
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


def test_suite_stackedensemble_base_models(blending=False):

    def test_base_models_can_be_passed_as_objects_or_as_ids():
        """This test checks the following:
        1) That passing in a list of models for base_models works.
        2) That passing in a list of models and model_ids results in the same stacked ensemble.
        """
        ds = prepare_data(blending)
        base_models = train_base_models(ds)
        se1 = train_stacked_ensemble(ds, [m.model_id for m in base_models])
        se2 = train_stacked_ensemble(ds, base_models)

        # Eval train AUC to assess equivalence
        assert se1.auc() == se2.auc()
        
    return [pu.tag_test(test, 'blending' if blending else None) for test in [
        test_base_models_can_be_passed_as_objects_or_as_ids
    ]]


# PUBDEV-6787
def test_base_models_are_populated():
    train = h2o.import_file(pu.locate("smalldata/iris/iris_train.csv"))
    test = h2o.import_file(pu.locate("smalldata/iris/iris_test.csv"))
    x = train.columns
    y = "species"
    x.remove(y)

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True)
    gbm.train(x=x, y=y, training_frame=train)
    rf = H2ORandomForestEstimator(nfolds=nfolds,
                                  fold_assignment="Modulo",
                                  keep_cross_validation_predictions=True)
    rf.train(x=x, y=y, training_frame=train)
    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     validation_frame=test,
                                     base_models=[gbm.model_id, rf.model_id])
    se.train(x=x, y=y, training_frame=train)
    retrieved_se = get_model(se.model_id)

    assert len(se.base_models) == 2
    assert len(retrieved_se.base_models) == 2
    assert se.base_models == retrieved_se.base_models
    # ensure that we are getting the model_ids
    assert pu.is_type(se.base_models, [str])
    assert pu.is_type(retrieved_se.base_models, [str])


pu.run_tests([
    test_suite_stackedensemble_base_models(),
    test_suite_stackedensemble_base_models(blending=True),
    test_base_models_are_populated
])
