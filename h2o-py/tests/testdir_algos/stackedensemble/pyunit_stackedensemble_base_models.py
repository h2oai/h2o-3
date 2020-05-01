#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import sys
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.grid import H2OGridSearch
from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from h2o.exceptions import H2OResponseError
from h2o import get_model
from tests import pyunit_utils as pu


seed = 1


def prepare_data(blending=False):
    fr = h2o.import_file(path=pu.locate("smalldata/junit/weather.csv"))
    target = "RainTomorrow"
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


# PUBDEV-4534
def test_stacked_ensemble_accepts_mixed_definition_of_base_models():
    """This test asserts that base models can be one of these:
    * list of models
    * GridSearch
    * list of GridSearches
    * list of Gridsearches and models
    """
    def _prepare_test_env():
        hyper_parameters = dict()
        hyper_parameters["ntrees"] = [1, 3, 5]
        params = dict(
            fold_assignment="modulo",
            nfolds=3,
            keep_cross_validation_predictions=True
        )

        data = prepare_data()

        drf = H2ORandomForestEstimator(**params)
        drf.train(data.x, data.y, data.train, validation_frame=data.train)

        gs1 = H2OGridSearch(H2OGradientBoostingEstimator(**params), hyper_params=hyper_parameters)
        gs1.train(data.x, data.y, data.train, validation_frame=data.train)

        gs2 = H2OGridSearch(H2ORandomForestEstimator(**params), hyper_params=hyper_parameters)
        gs2.train(data.x, data.y, data.train, validation_frame=data.train)

        return dict(data=data, drf=drf, gs1=gs1, gs2=gs2)

    def test_base_models_work_properly_with_list_of_models():
        env = _prepare_test_env()
        se = H2OStackedEnsembleEstimator(base_models=[env["drf"]])
        se.train(env["data"].x, env["data"].y, env["data"].train)
        assert se.base_models == [env["drf"].model_id], "StackedEnsembles don't work properly with single model in base models"

    def test_validation_on_backend_works():
        data = prepare_data()
        se = H2OStackedEnsembleEstimator(base_models=[data.train.frame_id])
        try:
            se.train(data.x, data.y, data.train)
        except TypeError as e:
            print(e)
            assert "Unsupported type of base model" in str(e), \
                "StackedEnsembles' base models validation exception probably changed."
        else:
            assert False, "StackEnsembles' base models validation doesn't work properly."

    def _check_base_models(name, base_models_1, base_models_2, error_message):
        def test():
            env = _prepare_test_env()
            se1 = H2OStackedEnsembleEstimator(base_models=base_models_1(env), seed=seed)
            se1.train(env["data"].x, env["data"].y, env["data"].train)
            se2 = H2OStackedEnsembleEstimator(base_models=base_models_2(env), seed=seed)
            se2.train(env["data"].x, env["data"].y, env["data"].train)
            assert sorted(se1.base_models) == sorted(se2.base_models), error_message
        test.__name__ = "test_stackedensembles_base_models_{}".format(name)
        return test

    test_cases = [
        {"name": "expand_single_grid",
         "base_models_1": lambda env: env["gs1"].models,
         "base_models_2": lambda env: env["gs1"],
         "error_message": "StackedEnsembles don't expand properly single grid."},
        {"name": "expand_multiple_grids_in_list",
         "base_models_1": lambda env: env["gs1"].models + env["gs2"].models,
         "base_models_2": lambda env: [env["gs1"], env["gs2"]],
         "error_message": "StackedEnsembles don't expand properly multiple grids in a list."},
        {"name": "expand_mixture_of_grids_and_models",
         "base_models_1": lambda env: env["gs1"].models + [env["drf"]] + env["gs2"].models,
         "base_models_2": lambda env: [env["gs1"], env["drf"], env["gs2"]],
         "error_message": "StackedEnsembles don't expand properly with mixture of grids and models."},
        {"name": "expand_mixture_of_grid_ids_and_model_ids",
         "base_models_1": lambda env: env["gs1"].models + [env["drf"]] + env["gs2"].models,
         "base_models_2": lambda env: [m.model_id for m in env["gs1"].models + [env["drf"]] + env["gs2"].models],
         "error_message": "StackedEnsembles don't work properly with mixture of grids id and model ids."},
    ]

    return [test_base_models_work_properly_with_list_of_models,
            test_validation_on_backend_works] + [
        _check_base_models(**kwargs) for kwargs in test_cases
    ]


pu.run_tests([
    test_suite_stackedensemble_base_models(),
    test_suite_stackedensemble_base_models(blending=True),
    test_base_models_are_populated,
    test_stacked_ensemble_accepts_mixed_definition_of_base_models(),
])
