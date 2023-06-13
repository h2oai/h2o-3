#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys

sys.path.insert(1, "../../")
from tests import pyunit_utils
import numpy as np
from sklearn.datasets import make_regression
from h2o import H2OFrame
from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator


def test_isotonic_regression_nfolds():
    X, y = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
    X = X.reshape(-1)

    train = H2OFrame(np.column_stack((y, X)), column_names=["y", "X"])

    h2o_iso_reg = H2OIsotonicRegressionEstimator(training_frame=train, nfolds=2, out_of_bounds="clip")
    h2o_iso_reg.train(x="X", y="y")

    print(h2o_iso_reg.model_performance())
    print(h2o_iso_reg.model_performance(xval=True))
    assert h2o_iso_reg.model_performance(xval=True)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_isotonic_regression_nfolds)
else:
    test_isotonic_regression_nfolds()
