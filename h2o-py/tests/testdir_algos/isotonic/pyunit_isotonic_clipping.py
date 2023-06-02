#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys

sys.path.insert(1, "../../")
from tests import pyunit_utils
import numpy as np
import pandas as pd
from pandas.testing import assert_frame_equal
from sklearn.datasets import make_regression
from sklearn.isotonic import IsotonicRegression
from h2o import H2OFrame
from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator


def test_isotonic_regression_clipping():
    X_full, y_full = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
    X_full = X_full.reshape(-1)

    p05 = np.quantile(X_full, 0.05)
    p95 = np.quantile(X_full, 0.95)
    
    X = X_full[np.logical_and(p05 < X_full, X_full < p95)]
    y = y_full[np.logical_and(p05 < X_full, X_full < p95)]

    # run sklearn Isotonic Regression to extract thresholds
    iso_reg = IsotonicRegression(out_of_bounds="clip").fit(X, y)

    # now invoke H2O Isotonic Regression
    train = H2OFrame(np.column_stack((y, X)), column_names=["y", "X"])
    h2o_iso_reg = H2OIsotonicRegressionEstimator(out_of_bounds="clip")
    h2o_iso_reg.train(training_frame=train, x="X", y="y")

    test = H2OFrame(np.column_stack((y_full, X_full)), column_names=["y", "X"])
    h2o_test_preds = h2o_iso_reg.predict(test).as_data_frame()

    test_preds = pd.DataFrame(iso_reg.predict(X_full), columns=["predict"])
    assert_frame_equal(test_preds, h2o_test_preds)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_isotonic_regression_clipping)
else:
    test_isotonic_regression_clipping()
