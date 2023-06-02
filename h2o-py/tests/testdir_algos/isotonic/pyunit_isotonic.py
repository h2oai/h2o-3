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
import h2o
from h2o import H2OFrame
from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator


def test_isotonic_regression(y, X, w):
    X = X.reshape(-1)
    # run sklearn Isotonic Regression to extract thresholds
    iso_reg = IsotonicRegression().fit(X, y, w)
    thresholds_scikit = get_thresholds(iso_reg)

    # now invoke H2O Isotonic Regression
    train = H2OFrame(np.column_stack((y, X, w)), column_names=["y", "X", "w"])
    h2o_iso_reg = H2OIsotonicRegressionEstimator()
    h2o_iso_reg.train(training_frame=train, x="X", y="y", weights_column="w")
    print(h2o_iso_reg)

    output = h2o_iso_reg._model_json["output"]

    # check that H2O's thresholds are the same a scikit's
    assert np.allclose(thresholds_scikit[0], np.array(output["thresholds_y"]))
    assert np.allclose(thresholds_scikit[1], np.array(output["thresholds_x"]))
    assert iso_reg.X_min_ == output["min_x"]
    assert iso_reg.X_max_ == output["max_x"]

    # test predict
    predicted = pd.DataFrame(iso_reg.predict(X), columns=["predict"])
    predicted_h2o = h2o_iso_reg.predict(train).as_data_frame(use_pandas=True)
    assert_frame_equal(predicted, predicted_h2o)

    # test MOJO predict
    mojo_iso_reg = pyunit_utils.download_mojo(h2o_iso_reg)
    train_pd = train.as_data_frame()
    predicted_mojo = h2o.mojo_predict_pandas(dataframe=train_pd, predict_calibrated=True, **mojo_iso_reg)
    # note: MOJO predicts also for 0 weights, we need to exclude those from comparison
    assert_frame_equal(predicted[train_pd["w"] != 0], predicted_mojo[train_pd["w"] != 0])

    # predict with out-of-bounds values (should produce NaNs)
    X_out_of_bounds = np.array([X.min() - 0.1, X.max() + 0.1])
    predicted_ooo = pd.DataFrame(iso_reg.predict(X_out_of_bounds), columns=["predict"])
    predicted_ooo_h2o = h2o_iso_reg.predict(H2OFrame(pd.DataFrame(X_out_of_bounds, columns=["X"]))).as_data_frame(use_pandas=True)
    assert_frame_equal(predicted_ooo, predicted_ooo_h2o)


def is_old_sklearn(fitted):
    # to be compatible with old sklearn (reference version 0.22.1)
    return hasattr(fitted, '_necessary_X_')


def get_thresholds(iso_reg):
    if is_old_sklearn(iso_reg):
        return iso_reg._necessary_y_, iso_reg._necessary_X_
    else:
        return iso_reg.y_thresholds_, iso_reg.X_thresholds_


def test_iso_reg_trivial():
    X = np.array([0.1, 0.2, 0.3])
    y = np.array([0.1, 0.2, 0.3])
    w = np.array([1.0, 1.0, 1.0])
    test_isotonic_regression(y, X, w)


def test_iso_reg_constant_weights():
    X, y = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
    w = np.full(y.shape, 1)
    test_isotonic_regression(y, X, w)


def test_iso_reg_random_weights():
    X, y = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
    w = np.random.random_sample(y.shape)
    test_isotonic_regression(y, X, w)


def test_iso_reg_01_weights():
    X, y = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
    w = np.random.randint(low=0, high=2, size=y.shape)
    test_isotonic_regression(y, X, w)


if __name__ == "__main__":
    pyunit_utils.run_tests([
        test_iso_reg_trivial,
        test_iso_reg_constant_weights,
        test_iso_reg_random_weights,
        test_iso_reg_01_weights
    ])
else:
    test_iso_reg_trivial()
    test_iso_reg_constant_weights()
    test_iso_reg_random_weights()
    test_iso_reg_01_weights()
