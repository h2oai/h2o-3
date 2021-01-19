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
from h2o.model.metrics_base import H2OBinomialModelMetrics
base_metric_methods = ['aic', 'auc', 'gini', 'logloss', 'mae', 'mean_per_class_error', 'mean_residual_deviance', 'mse',
                       'nobs', 'aucpr', 'pr_auc', 'r2', 'rmse', 'rmsle',
                       'residual_deviance', 'residual_degrees_of_freedom', 'null_deviance', 'null_degrees_of_freedom']
max_metrics = list(H2OBinomialModelMetrics.maximizing_metrics)


def pyunit_make_metrics(weights_col=None):
    fr = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    fr["CAPSULE"] = fr["CAPSULE"].asfactor()
    fr["RACE"] = fr["RACE"].asfactor()
    fr.describe()

    response = "AGE"
    predictors = list(set(fr.names) - {"ID", response})

    weights = None
    if weights_col:
        weights = h2o.assign(fr.runif(42), "weights")
        fr[weights_col] = weights

    print("\n\n======= REGRESSION ========\n")
    for distr in ["gaussian", "poisson", "laplace", "gamma"]:
        # Skipping on `laplace`
        # GBM training fails due to a bug: https://0xdata.atlassian.net/browse/PUBDEV-7480
        if weights_col is not None and distr == "laplace":
            continue
        print("distribution: %s" % distr)
        model = H2OGradientBoostingEstimator(distribution=distr, ntrees=2, max_depth=3,
                    min_rows=1, learn_rate=0.1, nbins=20, weights_column=weights_col)
        model.train(x=predictors, y=response, training_frame=fr)
        predicted = h2o.assign(model.predict(fr), "pred")
        actual = fr[response]

        m0 = model.model_performance(train=True)
        m1 = h2o.make_metrics(predicted, actual, distribution=distr, weights=weights)
        m2 = h2o.make_metrics(predicted, actual, weights=weights)
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
                                         learn_rate=0.01, nbins=20, seed=1, weights_column=weights_col)
    model.train(x=predictors, y=response, training_frame=fr)
    predicted = h2o.assign(model.predict(fr)[2], "pred")
    actual = h2o.assign(fr[response].asfactor(), "act")
    domain = ["0", "1"]

    m0 = model.model_performance(train=True)
    m1 = h2o.make_metrics(predicted, actual, domain=domain, weights=weights)
    m2 = h2o.make_metrics(predicted, actual, weights=weights)
    print("m0:")
    print(m0)
    print("m1:")
    print(m1)
    print("m2:")
    print(m2)

    assert m0.accuracy()[0][1] + m0.error()[0][1] == 1
    assert len(m0.accuracy(thresholds='all')) == len(m0.fprs)

    assert m0.accuracy().value == m1.accuracy().value == m0.accuracy()[0][1]
    assert m0.accuracy().value + m0.error().value == 1

    assert isinstance(m0.accuracy(thresholds=0.4).value, float)
    assert m0.accuracy(thresholds=0.4).value == m1.accuracy(thresholds=0.4).value == m0.accuracy(thresholds=0.4)[0][1]
    assert m0.accuracy(thresholds=0.4).value + m0.error(thresholds=0.4).value == 1

    assert isinstance(m0.accuracy(thresholds=[0.4]).value, list)
    assert len(m0.accuracy(thresholds=[0.4]).value) == 1
    assert m0.accuracy(thresholds=[0.4]).value[0] == m0.accuracy(thresholds=0.4).value

    assert isinstance(m0.accuracy(thresholds=[0.4, 0.5]).value, list)
    assert len(m0.accuracy(thresholds=[0.4, 0.5]).value) == 2
    assert m0.accuracy(thresholds=[0.4, 0.5]).value == [m0.accuracy(thresholds=0.4).value, m0.accuracy(thresholds=0.5).value]

    # Testing base metric methods
    # FIXME: check the same failures for other ModelMetrics impl. and then fix'emall or move them out of base class...
    base_metrics_methods_failing_on_H2OBinomialModelMetrics = ['aic', 'mae', 'mean_per_class_error', 'mean_residual_deviance', 'rmsle']
    for metric_method in (m for m in base_metric_methods if m not in base_metrics_methods_failing_on_H2OBinomialModelMetrics):
        m0mm = getattr(m0, metric_method)()
        m1mm = getattr(m1, metric_method)()
        m2mm = getattr(m2, metric_method)()

        assert m0mm == m1mm or abs(m0mm - m1mm) < 1e-5, \
            "{} is different for model_performance and make_metrics on [0, 1] domain".format(metric_method)
        assert m1mm == m2mm or abs(m1mm - m2mm) < 1e-5, \
            "{} is different for make_metrics on [0, 1] domain and make_metrics without domain".format(metric_method)
    # FIXME: for binomial mean_per_class_error is strangely accessible as an array
    assert abs(m0.mean_per_class_error()[0][1] - m1.mean_per_class_error()[0][1]) < 1e-5
    assert abs(m2.mean_per_class_error()[0][1] - m1.mean_per_class_error()[0][1]) < 1e-5

    failures = 0
    for metric_method in base_metrics_methods_failing_on_H2OBinomialModelMetrics:
        for m in [m0, m1, m2]:
            try:
                assert isinstance(getattr(m, metric_method)(), float)
            except:
                failures += 1
    assert failures == 3 * len(base_metrics_methods_failing_on_H2OBinomialModelMetrics)

    # Testing binomial-only metric methods
    binomial_only_metric_methods = ['accuracy', 'F0point5', 'F1', 'F2', 'mcc',
                                    'max_per_class_error', 'mean_per_class_error',
                                    'precision', 'recall', 'specificity', 'fallout', 'missrate', 'sensitivity',
                                    'fpr', 'fnr', 'tpr', 'tnr']
    for metric_method in (m for m in binomial_only_metric_methods):
        # FIXME: not sure that returning a 2d-array is justified when not passing any threshold
        m0mm = getattr(m0, metric_method)()[0]
        m1mm = getattr(m1, metric_method)()[0]
        m2mm = getattr(m2, metric_method)()[0]
        assert m0mm == m1mm or abs(m0mm[1] - m1mm[1]) < 1e-5, \
            "{} is different for model_performance and make_metrics on [0, 1] domain".format(metric_method)
        assert m1mm == m2mm or abs(m1mm[1] - m2mm[1]) < 1e-5, \
            "{} is different for make_metrics on [0, 1] domain and make_metrics without domain".format(metric_method)

    # Testing confusion matrix
    cm0 = m0.confusion_matrix(metrics=max_metrics)
    assert len(cm0) == len(max_metrics)
    assert all([any(m in header for header in map(lambda cm: cm.table._table_header, cm0) for m in max_metrics)]), \
        "got duplicate CM headers, although all metrics are different"
    cm0t = m0.confusion_matrix(metrics=max_metrics, thresholds=[.3, .6])
    assert len(cm0t) == 2 + len(max_metrics)
    assert 2 == sum([not any(m in header for m in max_metrics) for header in map(lambda cm: cm.table._table_header, cm0t)]),  \
        "missing or duplicate headers without metric (thresholds only CMs)"
    assert all([any(m in header for header in map(lambda cm: cm.table._table_header, cm0t) for m in max_metrics)]), \
        "got duplicate CM headers, although all metrics are different"


    print("\n\n======= MULTINOMIAL ========\n")
    response = "RACE"
    predictors = list(set(fr.names) - {"ID", response})
    model = H2OGradientBoostingEstimator(distribution="multinomial", ntrees=2, max_depth=3, min_rows=1,
                                         learn_rate=0.01, nbins=20, weights_column=weights_col, auc_type="MACRO_OVR")
    model.train(x=predictors, y=response, training_frame=fr)
    predicted = h2o.assign(model.predict(fr)[1:], "pred")
    actual = h2o.assign(fr[response].asfactor(), "act")
    domain = fr[response].levels()[0]

    m0 = model.model_performance(train=True)
    m1 = h2o.make_metrics(predicted, actual, domain=domain, weights=weights, auc_type="MACRO_OVR")
    m2 = h2o.make_metrics(predicted, actual, weights=weights, auc_type="MACRO_OVR")

    assert abs(m0.mse() - m1.mse()) < 1e-5
    assert abs(m0.rmse() - m1.rmse()) < 1e-5
    assert abs(m0.logloss() - m1.logloss()) < 1e-5
    assert abs(m0.mean_per_class_error() - m1.mean_per_class_error()) < 1e-5
    assert abs(m0.auc() - m1.auc()) < 1e-5
    assert abs(m0.aucpr() - m1.aucpr()) < 1e-5

    assert abs(m2.mse() - m1.mse()) < 1e-5
    assert abs(m2.rmse() - m1.rmse()) < 1e-5
    assert abs(m2.logloss() - m1.logloss()) < 1e-5
    assert abs(m2.mean_per_class_error() - m1.mean_per_class_error()) < 1e-5
    assert abs(m2.auc() - m1.auc()) < 1e-5
    assert abs(m2.aucpr() - m1.aucpr()) < 1e-5


def suite_model_metrics():

    def test_model_metrics_basic():
        pyunit_make_metrics()

    def test_model_metrics_weights():
        pyunit_make_metrics(weights_col="weights")

    return [
        test_model_metrics_basic,
        test_model_metrics_weights
    ]


pyunit_utils.run_tests([
    suite_model_metrics()
])
