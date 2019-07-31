from __future__ import print_function
import importlib, inspect, os, sys

import numpy as np
from sklearn.datasets import make_classification, make_regression
from sklearn.model_selection import train_test_split
from sklearn.pipeline import make_pipeline, Pipeline

import h2o
from h2o.sklearn import H2OAutoMLEstimator, H2OAutoMLClassifier, H2OAutoMLRegressor
from sklearn.metrics import accuracy_score, log_loss, r2_score
from h2o.sklearn.wrapper import H2OConnectionMonitorMixin


sys.path.insert(1, os.path.join("..",".."))
from tests import pyunit_utils, Namespace as ns


"""
This test suite creates sklearn pipelines using either a mix of sklearn+H2O components,
or only H2O components. 
Then, it feeds them with H2O frames (more efficient and ensures compatibility with old API.)
or with numpy arrays to provide the simplest approach for users wanting to use H2O like any sklearn estimator.
"""

seed = 2019
init_connection_args = dict(strict_version_check=False, show_progress=True)
max_models = 3

scores = {}


def _ensure_connection_state(connected=True):
    if connected:
        # if we need a connection beforehand, create it if needed
        H2OConnectionMonitorMixin.init_connection(init_connection_args)
    else:
        # if we want to start afresh, close everything first
        H2OConnectionMonitorMixin.close_connection(force=True)



def _get_data(format='numpy', n_classes=2):
    generator = make_classification if n_classes > 0 else make_regression
    params = dict(n_samples=100, n_features=5, n_informative=n_classes or 2, random_state=seed)
    if generator is make_classification:
        params.update(n_classes=n_classes)

    X, y = generator(**params)
    X_train, X_test, y_train, y_test = train_test_split(X, y, random_state=seed)
    data = ns(X_train=X_train, X_test=X_test, y_train=y_train, y_test=y_test)
    if format == 'h2o':
        for k, v in data.__dict__.items():
            setattr(data, k, h2o.H2OFrame(v))
    return data


def test_binomial_classification_with_h2o_frames():
    _ensure_connection_state(connected=True)

    pipeline = make_pipeline(H2OAutoMLClassifier(seed=seed, init_connection_args=init_connection_args))
    pipeline.set_params(
        h2oautomlclassifier__max_models=max_models,
        h2oautomlclassifier__nfolds=3
    )
    pipeline.named_steps.h2oautomlclassifier.exclude_algos = ['XGBoost']

    data = _get_data(format='h2o', n_classes=2)
    assert isinstance(data.X_train, h2o.H2OFrame)
    pipeline.fit(data.X_train, data.y_train)
    assert len(pipeline.named_steps.h2oautomlclassifier._estimator.leaderboard) == max_models+2

    preds = pipeline.predict(data.X_test)
    assert isinstance(preds, h2o.H2OFrame)
    assert preds.dim == [len(data.X_test), 1]
    probs = pipeline.predict_proba(data.X_test)
    assert probs.dim == [len(data.X_test), 2]

    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = accuracy_score(data.y_test.as_data_frame().values, preds.as_data_frame().values)
    assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)


def test_multinomial_classification_with_numpy_frames():
    _ensure_connection_state(connected=False)

    pipeline = make_pipeline(H2OAutoMLClassifier(seed=seed, init_connection_args=init_connection_args))
    pipeline.set_params(
        h2oautomlclassifier__max_models=max_models,
        h2oautomlclassifier__nfolds=3
    )
    pipeline.named_steps.h2oautomlclassifier.exclude_algos = ['XGBoost']

    data = _get_data(format='numpy', n_classes=3)
    assert isinstance(data.X_train, np.ndarray)
    pipeline.fit(data.X_train, data.y_train)
    assert len(pipeline.named_steps.h2oautomlclassifier._estimator.leaderboard) == max_models+2

    preds = pipeline.predict(data.X_test)
    assert isinstance(preds, np.ndarray)
    assert preds.shape == (len(data.X_test),)
    probs = pipeline.predict_proba(data.X_test)
    assert probs.shape == (len(data.X_test), 3)
    assert np.allclose(np.sum(probs, axis=1), 1.), "`predict_proba` didn't return probabilities"

    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = accuracy_score(data.y_test, preds)
    assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)


def test_regression_with_numpy_frames():
    _ensure_connection_state(connected=False)

    pipeline = make_pipeline(H2OAutoMLRegressor(seed=seed, init_connection_args=init_connection_args))
    pipeline.set_params(
        h2oautomlregressor__max_models=max_models,
        h2oautomlregressor__nfolds=3
    )
    pipeline.named_steps.h2oautomlregressor.exclude_algos = ['XGBoost']

    data = _get_data(format='numpy', n_classes=0)
    assert isinstance(data.X_train, np.ndarray)
    pipeline.fit(data.X_train, data.y_train)
    assert len(pipeline.named_steps.h2oautomlregressor._estimator.leaderboard) == max_models+2

    preds = pipeline.predict(data.X_test)
    assert isinstance(preds, np.ndarray)
    assert preds.shape == (len(data.X_test),)
    try:
        pipeline.predict_proba(data.X_test)
    except AttributeError as e:
        assert "No `predict_proba` method" in str(e)

    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = r2_score(data.y_test, preds)
    assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)


def test_generic_estimator_for_classification():
    _ensure_connection_state(connected=False)

    pipeline = make_pipeline(H2OAutoMLEstimator(estimator_type='classifier', seed=seed,
                                                init_connection_args=init_connection_args))
    pipeline.set_params(
        h2oautomlestimator__max_models=max_models,
        h2oautomlestimator__nfolds=3
    )
    pipeline.named_steps.h2oautomlestimator.exclude_algos = ['XGBoost']

    data = _get_data(format='numpy', n_classes=3)
    assert isinstance(data.X_train, np.ndarray)

    pipeline.fit(data.X_train, data.y_train)
    assert len(pipeline.named_steps.h2oautomlestimator._estimator.leaderboard) == max_models+2

    preds = pipeline.predict(data.X_test)
    assert isinstance(preds, np.ndarray)
    assert preds.shape == (len(data.X_test),)
    probs = pipeline.predict_proba(data.X_test)
    assert probs.shape == (len(data.X_test), 3)
    assert np.allclose(np.sum(probs, axis=1), 1.), "`predict_proba` didn't return probabilities"

    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = accuracy_score(data.y_test, preds)
    assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)



def test_generic_estimator_for_regression():
    _ensure_connection_state(connected=False)

    pipeline = make_pipeline(H2OAutoMLEstimator(estimator_type='regressor', seed=seed,
                                                init_connection_args=init_connection_args))
    pipeline.set_params(
        h2oautomlestimator__max_models=max_models,
        h2oautomlestimator__nfolds=3
    )
    pipeline.named_steps.h2oautomlestimator.exclude_algos = ['XGBoost']

    data = _get_data(format='numpy', n_classes=0)
    assert isinstance(data.X_train, np.ndarray)
    pipeline.fit(data.X_train, data.y_train)
    assert len(pipeline.named_steps.h2oautomlestimator._estimator.leaderboard) == max_models+2

    preds = pipeline.predict(data.X_test)
    assert isinstance(preds, np.ndarray)
    assert preds.shape == (len(data.X_test),)
    try:
        pipeline.predict_proba(data.X_test)
    except AttributeError as e:
        assert "No `predict_proba` method" in str(e)

    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = r2_score(data.y_test, preds)
    assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)



pyunit_utils.run_tests([
    test_binomial_classification_with_h2o_frames,
    test_multinomial_classification_with_numpy_frames,
    test_regression_with_numpy_frames,
    test_generic_estimator_for_classification,
    test_generic_estimator_for_regression,
])
