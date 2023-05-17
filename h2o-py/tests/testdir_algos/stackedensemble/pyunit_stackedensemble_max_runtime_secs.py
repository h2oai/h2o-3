#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o
import sys

from h2o.exceptions import H2OResponseError

sys.path.insert(1, "../../../")  # allow us to run this standalone

from h2o.grid import H2OGridSearch
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils as pu

seed = 1


def prepare_data():
    fr = h2o.import_file(path=pu.locate("smalldata/junit/weather.csv"))
    target = "RainTomorrow"
    fr[target] = fr[target].asfactor()
    ds = pu.ns(x=fr.columns, y=target, train=fr)

    return ds


def test_stackedensemble_propagates_the_max_runtime_secs():
    max_runtime_secs = 5
    hyper_parameters = dict()
    hyper_parameters["ntrees"] = [1, 3, 5]
    params = dict(
        fold_assignment="modulo",
        nfolds=3,
        keep_cross_validation_predictions=True
    )

    data = prepare_data()

    gs1 = H2OGridSearch(H2OGradientBoostingEstimator(**params), hyper_params=hyper_parameters)
    gs1.train(data.x, data.y, data.train, validation_frame=data.train)

    se = H2OStackedEnsembleEstimator(base_models=[gs1], max_runtime_secs=max_runtime_secs)
    se.train(data.x, data.y, data.train)
    metalearner = h2o.get_model(se.metalearner()["name"])

    # metalearner has the set max_runtine_secs
    assert metalearner.actual_params['max_runtime_secs'] <= max_runtime_secs
    assert metalearner.actual_params['max_runtime_secs'] > 0

    # stack ensemble has the set max_runtime_secs
    assert se.max_runtime_secs == max_runtime_secs


def test_stackedensemble_respects_the_max_runtime_secs():
    max_runtime_secs = 1
    hyper_parameters = dict()
    hyper_parameters["ntrees"] = [1, 2, 3, 4, 5]
    params = dict(
        fold_assignment="modulo",
        nfolds=3
    )

    data = prepare_data()

    gs1 = H2OGridSearch(H2OGradientBoostingEstimator(**params), hyper_params=hyper_parameters)
    gs1.train(data.x, data.y, data.train, validation_frame=data.train)

    big_blending_frame = data.train
    for i in range(15):
        big_blending_frame = big_blending_frame.rbind(big_blending_frame)

    se = H2OStackedEnsembleEstimator(
        base_models=gs1.model_ids,
        max_runtime_secs=max_runtime_secs,
        blending_frame=big_blending_frame)
    try:
        se.train(data.x, data.y, data.train)
        assert False, "This should have failed due to time out."
    except H2OResponseError:
        pass


pu.run_tests([
    test_stackedensemble_propagates_the_max_runtime_secs,
    test_stackedensemble_respects_the_max_runtime_secs,
])
