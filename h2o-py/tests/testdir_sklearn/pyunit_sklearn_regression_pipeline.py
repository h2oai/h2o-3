from __future__ import print_function
import importlib, inspect, os, sys

import numpy as np
from sklearn.datasets import make_regression
from sklearn.decomposition import PCA
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

import h2o
from h2o.sklearn import H2OAutoMLRegressor, H2OGradientBoostingRegressor, H2OScaler, H2OPCA
from sklearn.metrics import r2_score
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


def _ensure_connection_state(connected=True):
    if connected:
        # if we need a connection beforehand, create it if needed
        H2OConnectionMonitorMixin.init_connection(init_connection_args)
    else:
        # if we want to start afresh, close everything first
        H2OConnectionMonitorMixin.close_connection(force=True)



def _get_data(format='numpy'):
    X, y = make_regression(n_samples=1000, n_features=10, random_state=seed)
    X_train, X_test, y_train, y_test = train_test_split(X, y, random_state=seed)
    data = ns(X_train=X_train, X_test=X_test, y_train=y_train, y_test=y_test)
    if format == 'h2o':
        for k, v in data.__dict__.items():
            setattr(data, k, h2o.H2OFrame(v))
    return data


def test_h2o_only_pipeline_with_h2o_frames():
    _ensure_connection_state(connected=True)
    pipeline = Pipeline([
        ('standardize', H2OScaler(init_connection_args=init_connection_args)),
        ('pca', H2OPCA(k=2, seed=seed)),
        ('estimator', H2OGradientBoostingRegressor(seed=seed))
    ])
    data = _get_data(format='h2o')
    assert isinstance(data.X_train, h2o.H2OFrame)
    pipeline.fit(data.X_train, data.y_train)
    preds = pipeline.predict(data.X_test)
    assert isinstance(preds, h2o.H2OFrame)
    assert preds.dim == [len(data.X_test), 1]
    try:
        pipeline.predict_proba(data.X_test)
    except AttributeError as e:
        assert "No `predict_proba` method" in str(e)

    # this block shows how it should work in perfect world,
    # but given that `h2o.scale` modified X_test inplace during predictions,
    # and as it doesn't seem to be idempotent
    # then the next calls to score actually predicts on different data!!!
    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = r2_score(data.y_test.as_data_frame().values, preds.as_data_frame().values)
    assert abs(score - skl_score) > 1e-6, "great!, h2o.scale inplace modification of H2O frames has been fixed. "

    # to get it working, we need to score a fresh H2OFrame
    data = _get_data(format='h2o')
    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = r2_score(data.y_test.as_data_frame().values, preds.as_data_frame().values)
    assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)


def test_h2o_only_pipeline_with_numpy_arrays():
    _ensure_connection_state(connected=False)
    # Note that in normal situations (release build), init_connection_args can be omitted
    # otherwise, it should be set to the first H2O element in the pipeline.
    # Also note that in this specific case mixing numpy inputs with a fully H2O pipeline,
    # the last estimator requires the `data_conversion=True` param in order to return numpy arrays in predictions.
    pipeline = Pipeline([
        ('standardize', H2OScaler(init_connection_args=init_connection_args)),
        ('pca', H2OPCA(k=2, seed=seed)),
        ('estimator', H2OGradientBoostingRegressor(seed=seed, data_conversion=True))
    ])
    data = _get_data(format='numpy')
    assert isinstance(data.X_train, np.ndarray)
    pipeline.fit(data.X_train, data.y_train)
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
    assert abs(score - skl_score) < 1e-6


def test_mixed_pipeline_with_numpy_arrays():
    _ensure_connection_state(connected=False)
    # Note that in normal situations (release build), init_connection_args can be omitted
    # otherwise, it should be set to the first H2O element in the pipeline
    pipeline = Pipeline([
        ('standardize', StandardScaler()),
        ('pca', PCA(n_components=2, random_state=seed)),
        ('estimator', H2OGradientBoostingRegressor(seed=seed, init_connection_args=init_connection_args))
    ])
    data = _get_data(format='numpy')
    assert isinstance(data.X_train, np.ndarray)
    pipeline.fit(data.X_train, data.y_train)
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
    assert abs(score - skl_score) < 1e-6


pyunit_utils.run_tests([
    test_h2o_only_pipeline_with_h2o_frames,
    test_h2o_only_pipeline_with_numpy_arrays,
    test_mixed_pipeline_with_numpy_arrays,
])
