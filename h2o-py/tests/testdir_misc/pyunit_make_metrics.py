# -*- encoding: utf-8 -*-
"""
Test suite for h2o.make_metrics().

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
import sys
sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
from h2o.estimators import H2OGradientBoostingEstimator
from h2o.model import H2OBinomialModelMetrics
from h2o.estimators import H2OUpliftRandomForestEstimator

base_metric_methods = ['aic', 'loglikelihood', 'auc', 'gini', 'logloss', 'mae', 'mean_per_class_error', 'mean_residual_deviance', 'mse',
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
        # GBM training fails due to a bug: https://github.com/h2oai/h2o-3/issues/8158
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
    base_metrics_methods_failing_on_H2OBinomialModelMetrics = ['aic', 'loglikelihood', 'mae', 'mean_per_class_error', 'mean_residual_deviance', 'rmsle']
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


def pyunit_make_metrics_uplift():
    print("======= UPLIFT BINOMIAL ========")
    treatment_column = "treatment"
    response_column = "outcome"
    feature_cols = ["feature_"+str(x) for x in range(1,13)]

    train = h2o.import_file(pyunit_utils.locate("smalldata/uplift/upliftml_train.csv"))
    train[treatment_column] = train[treatment_column].asfactor()
    train[response_column] = train[response_column].asfactor()

    test = h2o.import_file(pyunit_utils.locate("smalldata/uplift/upliftml_test.csv"))
    test[treatment_column] = test[treatment_column].asfactor()
    test[response_column] = test[response_column].asfactor()

    nbins = 20
    model = H2OUpliftRandomForestEstimator(
        treatment_column=treatment_column,
        seed=42,
        auuc_nbins=nbins,
        score_each_iteration=True,
        ntrees=3
    )

    model.train(y=response_column, x=feature_cols, training_frame=train, validation_frame=test)
    # test on validation data, train metrics are affected by sample rate
    m0 = model.model_performance(valid=True)
    predicted = h2o.assign(model.predict(test)[0], "pred")
    actual = test[response_column]
    treatment = test[treatment_column]
    m1 = model.model_performance(test_data=test, auuc_type="AUTO")
    m2 = h2o.make_metrics(predicted, actual, treatment=treatment, auuc_type="AUTO", auuc_nbins=nbins)
    m3 = h2o.make_metrics(predicted, actual, treatment=treatment, auuc_type="AUTO",
                          custom_auuc_thresholds=m1.thresholds())
    m4 = h2o.make_metrics(predicted, actual, treatment=treatment, auuc_type="AUTO",
                          custom_auuc_thresholds=model.default_auuc_thresholds())
    new_nbins = nbins - 10
    m5 = h2o.make_metrics(predicted, actual, treatment=treatment, auuc_type="AUTO", auuc_nbins=new_nbins)

    print("Model AUUC: {}".format(model.auuc()))
    print("thresholds: {}".format(model.default_auuc_thresholds()))
    print("Model performance AUUC: {}".format(m0.auuc()))
    print("thresholds: {}".format(m0.thresholds()))
    print("Model performance AUUC: {}".format(m1.auuc()))
    print("thresholds: {}".format(m1.thresholds()))
    print("Make AUUC with no custom thresholds: {}".format(m2.auuc()))
    print("thresholds: {}".format(m2.thresholds()))
    print("Make AUUC with custom thresholds from m1: {}".format(m3.auuc()))
    print("thresholds: {}".format(m3.thresholds()))
    print("Make AUUC with custom thresholds from model defaults: {}".format(m4.auuc()))
    print("thresholds: {}".format(m4.thresholds()))
    print("Make AUUC with no custom thresholds but change nbins parameter: {}".format(m5.auuc()))
    print("thresholds: {}".format(m5.thresholds()))

    tol = 1e-5

    # default model auuc is calculated from train data, default thresholds are from validation data
    assert abs(model.auuc() - m0.auuc()) > tol
    # model performance calculates new thresholds but from the same data with the same number of bins, so AUUCs are same
    assert abs(m0.auuc() - m1.auuc()) < tol
    # make method calculates new thresholds but from the same data with the same number of bins, so AUUCs are same
    assert abs(m1.auuc() - m2.auuc()) < tol
    # if we use thresholds from performance metric and use it as custom, it makes the same metrics
    assert abs(m1.auuc() - m3.auuc()) < tol
    # make methods with different nbins parameter changes thresholds and AUUC
    assert abs(m3.auuc() - m5.auuc()) > tol

    print("===========================")


def suite_model_metrics():

    def test_model_metrics_basic():
        pyunit_make_metrics()

    def test_model_metrics_weights():
        pyunit_make_metrics(weights_col="weights")

    def test_model_metrics_uplift():
        pyunit_make_metrics_uplift()

    return [
        test_model_metrics_basic,
        test_model_metrics_weights,
        test_model_metrics_uplift
    ]


pyunit_utils.run_tests([
    suite_model_metrics()
])
