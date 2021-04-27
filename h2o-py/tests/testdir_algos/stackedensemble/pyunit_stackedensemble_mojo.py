#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import h2o

import sys
sys.path.insert(1, "../../../")  # allow us to run this standalone

from h2o.estimators.random_forest import H2ORandomForestEstimator
from h2o.estimators.gbm import H2OGradientBoostingEstimator
from h2o.estimators.stackedensemble import H2OStackedEnsembleEstimator
from tests import pyunit_utils as pu


seed = 1


def test_helper(train_path, test_path, target, classification, blending, metalearner_transform):
    train = h2o.import_file(path=pu.locate(train_path))
    test = h2o.import_file(path=pu.locate(test_path))
    if classification:
        train[target] = train[target].asfactor()
    if blending:
        train, blend = train.split_frame(ratios=[.7], seed=seed)

    model_args = dict() if blending else dict(nfolds=3, fold_assignment="Modulo", keep_cross_validation_predictions=True)

    gbm = H2OGradientBoostingEstimator(ntrees=10,
                                       seed=seed,
                                       **model_args)
    gbm.train(y=target, training_frame=train)

    rf = H2ORandomForestEstimator(ntrees=10,
                                  seed=seed,
                                  **model_args)
    rf.train(y=target, training_frame=train)

    se = H2OStackedEnsembleEstimator(base_models=[rf, gbm], metalearner_transform=metalearner_transform)
    se.train(y=target, training_frame=train, **(dict(blending_frame=blend) if blending else dict()))

    se_predictions = se.predict(test)

    import tempfile
    tmpdir = tempfile.mkdtemp()

    try:
        mojo_path = se.save_mojo(tmpdir)
        mojo_model = h2o.upload_mojo(mojo_path)
    finally:
        import shutil
        shutil.rmtree(tmpdir)

    mojo_predictions = mojo_model.predict(test)

    assert pu.compare_frames(se_predictions, mojo_predictions, 0)


def test_binomial_no_transform():
    test_helper("smalldata/prostate/prostate.csv", "smalldata/prostate/prostate.csv", "CAPSULE", True, False, "None")
    test_helper("smalldata/prostate/prostate.csv", "smalldata/prostate/prostate.csv", "CAPSULE", True, True, "None")


def test_binomial_logit_transform():
    test_helper("smalldata/prostate/prostate.csv", "smalldata/prostate/prostate.csv", "CAPSULE", True, False, "Logit")
    test_helper("smalldata/prostate/prostate.csv", "smalldata/prostate/prostate.csv", "CAPSULE", True, True, "Logit")


def test_multinomial_no_transform():
    test_helper("smalldata/iris/iris_train.csv", "smalldata/iris/iris_test.csv", "species", True, False, "None")
    test_helper("smalldata/iris/iris_train.csv", "smalldata/iris/iris_test.csv", "species", True, True, "None")


def test_multinomial_logit_transform():
    test_helper("smalldata/iris/iris_train.csv", "smalldata/iris/iris_test.csv", "species", True, False, "Logit")
    test_helper("smalldata/iris/iris_train.csv", "smalldata/iris/iris_test.csv", "species", True, True, "Logit")


def test_regression_no_transform():
    test_helper("smalldata/prostate/prostate.csv", "smalldata/prostate/prostate.csv", "AGE", False, False, "None")
    test_helper("smalldata/prostate/prostate.csv", "smalldata/prostate/prostate.csv", "AGE", False, True, "None")


def test_regression_logit_transform():
    try:
        test_helper("smalldata/prostate/prostate.csv", "smalldata/prostate/prostate.csv", "AGE", False, False, "Logit")
        assert False, "Should have failed since metalearner transform is not supported for regression"
    except (OSError, EnvironmentError) as e:
        if "Metalearner transform is supported only for classification" not in str(e):
            assert False, "Should have failed with metalearner transform  in the error message"
    try:
        test_helper("smalldata/prostate/prostate.csv", "smalldata/prostate/prostate.csv", "AGE", False, True, "Logit")
        assert False, "Should have failed since metalearner transform is not supported for regression"
    except (OSError, EnvironmentError) as e:
        if "Metalearner transform is supported only for classification" not in str(e):
            assert False, "Should have failed with metalearner transform in the error message"


pu.run_tests([
    test_binomial_no_transform,
    test_binomial_logit_transform,
    test_multinomial_no_transform,
    test_multinomial_logit_transform,
    test_regression_no_transform,
    test_regression_logit_transform
])
