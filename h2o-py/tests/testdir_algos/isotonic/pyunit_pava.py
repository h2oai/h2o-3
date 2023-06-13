#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys

sys.path.insert(1, "../../")
from tests import pyunit_utils
import numpy as np
from pandas.testing import assert_frame_equal
from sklearn.datasets import make_regression
from sklearn.isotonic import IsotonicRegression
from h2o import H2OFrame
from h2o.expr import ExprNode


def pav(y, X, w):
    # make H2O Frame (y, X, w)
    frame = H2OFrame(np.column_stack((y, X, w)))
    return H2OFrame._expr(expr=ExprNode("isotonic.pav", frame))[["C1", "C2"]]


def test_pav(y, X, w):
    X = X.reshape(-1)
    # run Isotonic Regression to extract thresholds
    iso_reg = IsotonicRegression().fit(X, y, w)
    thresholds_scikit = H2OFrame(np.column_stack(get_thresholds(iso_reg)))
    print(thresholds_scikit.as_data_frame())

    # now invoke H2O PAVA
    thresholds_h2o = pav(y, X, w)
    print(thresholds_h2o.as_data_frame())

    assert_frame_equal(thresholds_scikit.as_data_frame(), thresholds_h2o.as_data_frame())


def is_old_sklearn(fitted):
    # to be compatible with old sklearn (reference version 0.22.1)
    return hasattr(fitted, '_necessary_X_')


def get_thresholds(iso_reg):
    if is_old_sklearn(iso_reg):
        return iso_reg._necessary_y_, iso_reg._necessary_X_
    else:
        return iso_reg.y_thresholds_, iso_reg.X_thresholds_
    

def test_pav_trivial():
    X = np.array([0.1, 0.2, 0.3])
    y = np.array([0.1, 0.2, 0.3])
    w = np.array([1.0, 1.0, 1.0])
    test_pav(y, X, w)


def test_pav_constant_weights():
    X, y = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
    w = np.full(y.shape, 1)
    test_pav(y, X, w)


def test_pav_random_weights():
    X, y = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
    w = np.random.random_sample(y.shape)
    test_pav(y, X, w)


def test_pav_01_weights():
    X, y = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
    w = np.random.randint(low=0, high=2, size=y.shape)
    test_pav(y, X, w)


if __name__ == "__main__":
    pyunit_utils.run_tests([
        test_pav_trivial,
        test_pav_constant_weights,
        test_pav_random_weights,
        test_pav_01_weights
    ])
else:
    test_pav_trivial()
    test_pav_constant_weights()
    test_pav_random_weights()
    test_pav_01_weights()
