#!/usr/bin/env python
from __future__ import absolute_import, division, print_function, unicode_literals
import h2o
from tests import pyunit_utils

from h2o.estimators.xgboost import H2OXGBoostEstimator

def test_pubdev_4586():
    assert H2OXGBoostEstimator.available(), "H2O XGBoost is not available! Please check machine env!"

    train = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_train.csv"))
    test = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_test.csv"))
    x = train.columns
    y = "species"
    x.remove(y)

    xgb = H2OXGBoostEstimator(distribution="auto", ntrees=1, seed=1)
    xgb.train(x=x, y=y, training_frame=train)
    mm_train = xgb.model_performance(train)
    mm_test = xgb.model_performance(test)

    assert mm_train is not None, "Model metrics for train data is not null"
    assert mm_test is not None, "Model metrics for unseen data is not null"

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_pubdev_4586)
else:
    test_pubdev_4586()
