# -*- encoding: utf-8 -*-
"""
Test suite for h2o.make_metrics().

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import sys
sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator


def pyunit_make_metrics():
    fr = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    fr["CAPSULE"] = fr["CAPSULE"].asfactor()
    fr["RACE"] = fr["RACE"].asfactor()
    fr.describe()

    response = "AGE"
    predictors = list(set(fr.names) - {"ID", response})

    print("\n\n======= REGRESSION ========\n")
    for distr in ["gaussian", "poisson", "laplace", "gamma"]:
        print("distribution: %s" % distr)
        model = H2OGradientBoostingEstimator(distribution=distr, ntrees=2, max_depth=3,
                    min_rows=1, learn_rate=0.1, nbins=20)
        model.train(x=predictors, y=response, training_frame=fr)
        predicted = h2o.assign(model.predict(fr), "pred")
        actual = fr[response]

        m0 = model.model_performance(train=True)
        m1 = h2o.make_metrics(predicted, actual, distribution=distr)
        m2 = h2o.make_metrics(predicted, actual)
        print("model performance:")
        print(m0)
        print("make_metrics (distribution=%s):" % distr)
        print(m1)
        print("make_metrics (distribution=None):")
        print(m2)

        assert abs(m0.mae() - m1.mae()) < 1e-5
        assert abs(m0.mse() - m1.mse()) < 1e-5
        assert abs(m0.rmse() - m1.rmse()) < 1e-5
        assert abs(m0.mean_residual_deviance() - m1.mean_residual_deviance()) < 1e-5
        assert abs(m0.rmsle() - m1.rmsle()) < 1e-5

        assert abs(m2.mae() - m1.mae()) < 1e-5
        assert abs(m2.mse() - m1.mse()) < 1e-5
        assert abs(m2.rmse() - m1.rmse()) < 1e-5
        assert (abs(m1.mean_residual_deviance() - m2.mean_residual_deviance()) < 1e-7) == (distr == "gaussian")
        assert abs(m2.rmsle() - m1.rmsle()) < 1e-5

    print("\n\n======= BINOMIAL ========\n")
    response = "CAPSULE"
    predictors = list(set(fr.names) - {"ID", response})
    model = H2OGradientBoostingEstimator(distribution="bernoulli", ntrees=2, max_depth=3, min_rows=1,
                                         learn_rate=0.01, nbins=20)
    model.train(x=predictors, y=response, training_frame=fr)
    predicted = h2o.assign(model.predict(fr)[2], "pred")
    actual = h2o.assign(fr[response].asfactor(), "act")
    domain = ["0", "1"]

    m0 = model.model_performance(train=True)
    m1 = h2o.make_metrics(predicted, actual, domain=domain)
    m2 = h2o.make_metrics(predicted, actual)
    print("m0:")
    print(m0)
    print("m1:")
    print(m1)
    print("m2:")
    print(m2)

    assert abs(m0.auc() - m1.auc()) < 1e-5
    assert abs(m0.mse() - m1.mse()) < 1e-5
    assert abs(m0.rmse() - m1.rmse()) < 1e-5
    assert abs(m0.logloss() - m1.logloss()) < 1e-5
    assert abs(m0.mean_per_class_error()[0][1] - m1.mean_per_class_error()[0][1]) < 1e-5
    assert abs(m2.auc() - m1.auc()) < 1e-5
    assert abs(m2.mse() - m1.mse()) < 1e-5
    assert abs(m2.rmse() - m1.rmse()) < 1e-5
    assert abs(m2.logloss() - m1.logloss()) < 1e-5
    assert abs(m2.mean_per_class_error()[0][1] - m1.mean_per_class_error()[0][1]) < 1e-5


    print("\n\n======= MULTINOMIAL ========\n")
    response = "RACE"
    predictors = list(set(fr.names) - {"ID", response})
    model = H2OGradientBoostingEstimator(distribution="multinomial", ntrees=2, max_depth=3, min_rows=1,
                                         learn_rate=0.01, nbins=20)
    model.train(x=predictors, y=response, training_frame=fr)
    predicted = h2o.assign(model.predict(fr)[1:], "pred")
    actual = h2o.assign(fr[response].asfactor(), "act")
    domain = fr[response].levels()[0]

    m0 = model.model_performance(train=True)
    m1 = h2o.make_metrics(predicted, actual, domain=domain)
    m2 = h2o.make_metrics(predicted, actual)

    assert abs(m0.mse() - m1.mse()) < 1e-5
    assert abs(m0.rmse() - m1.rmse()) < 1e-5
    assert abs(m0.logloss() - m1.logloss()) < 1e-5
    assert abs(m0.mean_per_class_error() - m1.mean_per_class_error()) < 1e-5
    assert abs(m2.mse() - m1.mse()) < 1e-5
    assert abs(m2.rmse() - m1.rmse()) < 1e-5
    assert abs(m2.logloss() - m1.logloss()) < 1e-5
    assert abs(m2.mean_per_class_error() - m1.mean_per_class_error()) < 1e-5




if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_make_metrics)
else:
    pyunit_make_metrics()
