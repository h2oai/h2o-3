#!/usr/bin/env python
# -*- encoding: utf-8 -*-
import sys

sys.path.insert(1, "../../")
from tests import pyunit_utils
import numpy as np
from sklearn.datasets import make_regression
from h2o import H2OFrame
from h2o.estimators.isotonicregression import H2OIsotonicRegressionEstimator


def test_isotonic_regression_validation():
    X, y = make_regression(n_samples=10000, n_features=1, random_state=41, noise=0.8)
    X = X.reshape(-1)

    df = H2OFrame(np.column_stack((y, X)), column_names=["y", "X"])
    train, valid = df.split_frame(ratios=[0.2])

    h2o_iso_reg = H2OIsotonicRegressionEstimator(training_frame=train, validation_frame=valid, out_of_bounds="clip")
    h2o_iso_reg.train(x="X", y="y")

    print(h2o_iso_reg.model_performance())
    print(h2o_iso_reg.model_performance(valid=True))

    valid_copy = H2OFrame(valid.as_data_frame())
    valid_copy_performance = h2o_iso_reg.model_performance(test_data=valid_copy)
    print(valid_copy_performance)

    pyunit_utils.assertEqualModelMetrics(h2o_iso_reg.model_performance(valid=True), valid_copy_performance)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_isotonic_regression_validation)
else:
    test_isotonic_regression_validation()
