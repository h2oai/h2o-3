#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals

import h2o
from h2o.estimators import H2OGeneralizedLinearEstimator
from h2o.exceptions import H2OTypeError
from tests import pyunit_utils


def test_glm_params():
    H2OGeneralizedLinearEstimator()
    H2OGeneralizedLinearEstimator(nfolds=5, seed=1000, alpha=0.5)

    df = h2o.H2OFrame.from_python({"response": [1, 2, 3, 4, 5], "a": [0, 1, 0, 1, 0], "b": [-1, 3, 7, 11, 20],
                                   "n": [0] * 5, "w": [1] * 5})

    model = H2OGeneralizedLinearEstimator()
    model.training_frame = df
    model.validation_frame = df
    model.nfolds = 3
    model.keep_cross_validation_predictions = True
    model.keep_cross_validation_fold_assignment = True
    model.fold_assignment = "random"
    model.fold_column = "b"
    model.response_column = "response"
    model.ignored_columns = ["x", "y"]
    model.ignore_const_cols = True
    model.score_each_iteration = True
    model.offset_column = "n"
    model.weights_column = "w"
    model.family = "MultiNomial"
    model.family = "GAUSSIAN"
    model.family = "Twee-die"
    model.family = "'poIssoN'"
    model.tweedie_variance_power = 1
    model.tweedie_link_power = 2
    model.solver = "CoordinateDescentNaive"

    try:
        model.fold_assignment = "pseudo-random"
        assert False
    except H2OTypeError:
        pass

    try:
        model.ignored_columns = "c"
        assert False
    except H2OTypeError:
        pass



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_params)
else:
    test_glm_params()
