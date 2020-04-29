#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import sys
import warnings
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator

from tests import pyunit_utils as pu


def test_weights_column_is_propagated_to_metalearner():
    train = h2o.import_file(pu.locate("smalldata/iris/iris_train.csv"))
    train["weights"] = 1
    x = train.columns
    y = "species"
    x.remove(y)

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True,
                                       weights_column="weights"
                                       )
    gbm.train(x=x, y=y, training_frame=train)

    rf = H2ORandomForestEstimator(nfolds=nfolds,
                                  fold_assignment="Modulo",
                                  keep_cross_validation_predictions=True,
                                  weights_column="weights"
                                  )
    rf.train(x=x, y=y, training_frame=train)

    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     base_models=[gbm.model_id, rf.model_id],
                                     weights_column="weights")
    se.train(x=x, y=y, training_frame=train)

    assert se.metalearner().actual_params["weights_column"]["column_name"] == "weights"


def test_SE_warns_when_all_basemodels_use_same_weights_column_and_SE_none():
    train = h2o.import_file(pu.locate("smalldata/iris/iris_train.csv"))
    train["weights"] = 1
    x = train.columns
    y = "species"
    x.remove(y)

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True,
                                       weights_column="weights"
                                       )
    gbm.train(x=x, y=y, training_frame=train)

    rf = H2ORandomForestEstimator(nfolds=nfolds,
                                  fold_assignment="Modulo",
                                  keep_cross_validation_predictions=True,
                                  weights_column="weights"
                                  )
    rf.train(x=x, y=y, training_frame=train)

    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     base_models=[gbm.model_id, rf.model_id])

    for v in sys.modules.values():
        if getattr(v, '__warningregistry__', None):
            v.__warningregistry__ = {}
    with warnings.catch_warnings(record=True) as ws:
        # Get all UserWarnings
        warnings.simplefilter("always", UserWarning)
        se.train(x=x, y=y, training_frame=train)
        assert any((
            issubclass(w.category, UserWarning) and
            'use weights_column="weights"' in str(w.message)
            for w in ws
        ))


pu.run_tests([
    test_weights_column_is_propagated_to_metalearner,
    test_SE_warns_when_all_basemodels_use_same_weights_column_and_SE_none
])
