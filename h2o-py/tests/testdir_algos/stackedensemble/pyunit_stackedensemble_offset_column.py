#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import h2o

import sys
import warnings
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from h2o.exceptions import H2OResponseError

from tests import pyunit_utils as pu


def test_offset_column_is_propagated_to_metalearner():
    train = h2o.import_file(pu.locate("smalldata/iris/iris_train.csv"))
    x = train.columns
    y = "petal_wid"
    x.remove(y)

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True,
                                       offset_column="petal_len"
                                       )
    gbm.train(x=x, y=y, training_frame=train)

    gbm2 = H2OGradientBoostingEstimator(nfolds=nfolds,
                                        fold_assignment="Modulo",
                                        keep_cross_validation_predictions=True,
                                        offset_column="petal_len"
                                        )
    gbm2.train(x=x, y=y, training_frame=train)

    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     base_models=[gbm.model_id, gbm2.model_id],
                                     offset_column="petal_len")
    se.train(x=x, y=y, training_frame=train)

    assert se.metalearner().actual_params["offset_column"]["column_name"] == "petal_len"


def test_offset_column_is_inherited_from_base_models():
    train = h2o.import_file(pu.locate("smalldata/iris/iris_train.csv"))
    x = train.columns
    y = "petal_wid"
    x.remove(y)

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True,
                                       offset_column="petal_len"
                                       )
    gbm.train(x=x, y=y, training_frame=train)

    gbm2 = H2OGradientBoostingEstimator(nfolds=nfolds,
                                        fold_assignment="Modulo",
                                        keep_cross_validation_predictions=True,
                                        offset_column="petal_len"
                                        )
    gbm2.train(x=x, y=y, training_frame=train)

    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     base_models=[gbm.model_id, gbm2.model_id])
    se.train(x=x, y=y, training_frame=train)

    assert se.metalearner().actual_params["offset_column"]["column_name"] == "petal_len"


def test_offset_column_has_to_be_same_in_each_base_model():
    train = h2o.import_file(pu.locate("smalldata/iris/iris_train.csv"))
    x = train.columns
    y = "petal_wid"
    x.remove(y)

    nfolds = 2
    gbm = H2OGradientBoostingEstimator(nfolds=nfolds,
                                       fold_assignment="Modulo",
                                       keep_cross_validation_predictions=True,
                                       )
    gbm.train(x=x, y=y, training_frame=train)

    gbm2 = H2OGradientBoostingEstimator(nfolds=nfolds,
                                        fold_assignment="Modulo",
                                        keep_cross_validation_predictions=True,
                                        offset_column="petal_len"
                                        )
    gbm2.train(x=x, y=y, training_frame=train)

    gbm3 = H2OGradientBoostingEstimator(nfolds=nfolds,
                                        fold_assignment="Modulo",
                                        keep_cross_validation_predictions=True,
                                        offset_column="sepal_len"
                                        )
    gbm3.train(x=x, y=y, training_frame=train)

    try:
        se = H2OStackedEnsembleEstimator(training_frame=train,
                                         base_models=[gbm.model_id, gbm2.model_id],
                                         offset_column="petal_len")
        se.train(x=x, y=y, training_frame=train)
        assert False, "Should have failed with 'All base models have to have the same offset_column!'"
    except H2OResponseError:
        pass

    try:
        se = H2OStackedEnsembleEstimator(training_frame=train,
                                         base_models=[gbm2.model_id, gbm.model_id],
                                         offset_column="petal_len")
        se.train(x=x, y=y, training_frame=train)
        assert False, "Should have failed with 'All base models have to have the same offset_column!'"
    except H2OResponseError:
        pass

    try:
        se = H2OStackedEnsembleEstimator(training_frame=train,
                                         base_models=[gbm2.model_id, gbm3.model_id],
                                         offset_column="petal_len")
        se.train(x=x, y=y, training_frame=train)
        assert False, "Should have failed with 'All base models have to have the same offset_column!'"
    except H2OResponseError:
        pass

pu.run_tests([
    test_offset_column_is_propagated_to_metalearner,
    test_offset_column_is_inherited_from_base_models,
    test_offset_column_has_to_be_same_in_each_base_model,
])
