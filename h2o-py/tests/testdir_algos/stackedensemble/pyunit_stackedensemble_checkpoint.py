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


def create_checkpoint(fr, x, y, checkpoint, algo, blending=False):
    bm_parms = dict(nfolds=3, seed=1, keep_cross_validation_predictions=True)

    gbm = H2OGradientBoostingEstimator(**bm_parms)
    gbm.train(x, y, fr)

    drf = H2ORandomForestEstimator(**bm_parms)
    drf.train(x, y, fr)

    se = H2OStackedEnsembleEstimator(
        model_id=checkpoint,
        metalearner_algorithm=algo,
        base_models=[gbm, drf],
        blending_frame=fr if blending else None
    )
    se.train(x, y, fr)


def checkpoint_works_with_same_data_test():
    fr = h2o.import_file(path=pu.locate("smalldata/iris/setosa_versicolor.csv"))
    y = "C5"
    fr[y] = fr[y].asfactor()

    x = fr.columns
    x.remove(y)

    create_checkpoint(fr, x, y, "se_checkpoint", "gbm")

    se = H2OStackedEnsembleEstimator(base_models=[], checkpoint="se_checkpoint",
                                     metalearner_algorithm="gbm",
                                     metalearner_params=dict(ntrees=60))
    se.train(x, y, fr)
    assert se.metalearner().actual_params['ntrees'] == 60


def checkpoint_works_with_additional_data_test():
    fr = h2o.import_file(path=pu.locate("smalldata/iris/iris_train.csv"))
    fr2 = h2o.import_file(path=pu.locate("smalldata/iris/iris_test.csv"))

    y = "species"
    fr[y] = fr[y].asfactor()

    x = fr.columns
    x.remove(y)

    create_checkpoint(fr, x, y, "se_checkpoint", "gbm")

    se = H2OStackedEnsembleEstimator(base_models=[], checkpoint="se_checkpoint",
                                     metalearner_algorithm="gbm",
                                     metalearner_params=dict(ntrees=60))
    se.train(x, y, fr2)
    assert se.metalearner().actual_params['ntrees'] == 60


def checkpoint_fails_with_new_category_in_response_test():
    fr = h2o.import_file(path=pu.locate("smalldata/iris/setosa_versicolor.csv"))
    y = "C5"
    fr[y] = fr[y].asfactor()

    x = fr.columns
    x.remove(y)

    create_checkpoint(fr, x, y, "se_checkpoint", "gbm", blending=True)

    fr2 = h2o.import_file(path=pu.locate("smalldata/iris/virginica.csv"))

    se = H2OStackedEnsembleEstimator(base_models=[], checkpoint="se_checkpoint",
                                     metalearner_algorithm="gbm",
                                     metalearner_params=dict(ntrees=60),
                                     blending_frame=fr2)
    try:
        se.train(x, y, fr2)
        assert False, 'Should have failed with "java.lang.IllegalArgumentException: ' \
                      'Categorical factor levels of the training data must be the same ' \
                      'as for the checkpointed model"'
    except EnvironmentError:
        pass


def checkpoint_fails_with_new_category_in_predictors_test():
    fr = h2o.import_file(path=pu.locate("smalldata/iris/setosa_versicolor.csv"))
    y = "C4"
    fr["C5"] = fr["C5"].asfactor()

    x = fr.columns
    x.remove(y)

    create_checkpoint(fr, x, y, "se_checkpoint", "gbm", blending=True)

    fr2 = h2o.import_file(path=pu.locate("smalldata/iris/virginica.csv"))

    se = H2OStackedEnsembleEstimator(base_models=[], checkpoint="se_checkpoint",
                                     metalearner_algorithm="gbm",
                                     metalearner_params=dict(ntrees=60),
                                     blending_frame=fr2)
    try:
        se.train(x, y, fr)
        assert False, 'Should have failed with "Error: Blending frame has an unseen category by checkpointed model: C5 = Iris-setosa"'
    except EnvironmentError:
        pass


pu.run_tests([
    checkpoint_works_with_same_data_test,
    checkpoint_works_with_additional_data_test,
    checkpoint_fails_with_new_category_in_response_test,
    checkpoint_fails_with_new_category_in_predictors_test,
])
