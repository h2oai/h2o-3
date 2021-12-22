#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import print_function

import shutil
import tempfile

import h2o

import sys
import warnings
sys.path.insert(1,"../../../")  # allow us to run this standalone

from h2o.estimators import *
from tests import pyunit_utils as pu


def test_fold_column_is_used_properly_in_mojo():
    train = h2o.import_file(pu.locate("smalldata/iris/iris_train.csv"))
    test = h2o.import_file(pu.locate("smalldata/iris/iris_train.csv"))
    x = train.columns
    y = "petal_wid"
    x.remove(y)
    train["fold_col"] = h2o.H2OFrame([i % 5 for i in range(train.nrow)])
    test["fold_col"] = h2o.H2OFrame([i % 5 for i in range(test.nrow)])

    dl = H2ODeepLearningEstimator(keep_cross_validation_predictions=True,
                                  fold_column="fold_col")
    dl.train(x=x, y=y, training_frame=train)

    drf = H2ORandomForestEstimator(keep_cross_validation_predictions=True,
                                   fold_column="fold_col")
    drf.train(x=x, y=y, training_frame=train)

    gbm = H2OGradientBoostingEstimator(keep_cross_validation_predictions=True,
                                       fold_column="fold_col")
    gbm.train(x=x, y=y, training_frame=train)

    glm = H2OGeneralizedLinearEstimator(keep_cross_validation_predictions=True,
                                        fold_column="fold_col")
    glm.train(x=x, y=y, training_frame=train)

    se = H2OStackedEnsembleEstimator(training_frame=train,
                                     base_models=[gbm, drf, dl],
                                     metalearner_fold_column="fold_col")
    se.train(x=x, y=y, training_frame=train)

    try:
        tempdir = tempfile.mkdtemp()
        predictions = se.predict(test)
        mojoname = se.save_mojo(tempdir)
        mojo_model = h2o.import_mojo(mojoname)
        try:
            mojo_predictions1 = mojo_model.predict(test)
        except Exception:
            assert False, "Can't use the SE loaded from mojo to predict with the whole dataset including the fold_column"
        try:
            mojo_predictions2 = mojo_model.predict(test[x + [y]])  # without the fold column present
        except Exception:
            assert False, "Can't use the SE loaded from mojo to predict with the whole dataset without the fold_column"
        assert (predictions == mojo_predictions1).all()
        assert (predictions == mojo_predictions2).all()
    finally:
        shutil.rmtree(tempdir)


pu.run_tests([
    test_fold_column_is_used_properly_in_mojo,
])
