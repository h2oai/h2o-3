from __future__ import print_function
import importlib, inspect, os, sys

import numpy as np
from sklearn.datasets import make_classification
from sklearn.decomposition import TruncatedSVD
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline

import h2o
from h2o.sklearn import H2OGradientBoostingEstimator, H2OGradientBoostingClassifier, H2OSVD
from sklearn.metrics import accuracy_score
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

scores = {}

def _ensure_connection_state(connected=True):
    if connected:
        # if we need a connection beforehand, create it if needed
        H2OConnectionMonitorMixin.init_connection(init_connection_args)
    else:
        # if we want to start afresh, close everything first
        H2OConnectionMonitorMixin.close_connection(force=True)



def _get_data(format='numpy'):
    X, y = make_classification(n_samples=1000, n_features=10, n_informative=5, random_state=seed)
    X_train, X_test, y_train, y_test = train_test_split(X, y, random_state=seed)
    data = ns(X_train=X_train, X_test=X_test, y_train=y_train, y_test=y_test)
    if format == 'h2o':
        for k, v in data.__dict__.items():
            setattr(data, k, h2o.H2OFrame(v))
    return data


def test_h2o_only_pipeline_with_h2o_frames():
    _ensure_connection_state(connected=True)
    pipeline = Pipeline([
        ('svd', H2OSVD(nv=3, transform='DESCALE', seed=seed)),
        ('estimator', H2OGradientBoostingClassifier(seed=seed))
    ])
    data = _get_data(format='h2o')
    assert isinstance(data.X_train, h2o.H2OFrame)
    pipeline.fit(data.X_train, data.y_train)
    preds = pipeline.predict(data.X_test)
    assert isinstance(preds, h2o.H2OFrame)
    assert preds.dim == [len(data.X_test), 1]
    pipeline.predict_proba(data.X_test)

    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = accuracy_score(data.y_test.as_data_frame().values, preds.as_data_frame().values)
    assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)
    scores['h2o_only_pipeline_with_h2o_frame'] = score


def test_h2o_only_pipeline_with_numpy_arrays():
    _ensure_connection_state(connected=False)
    # Note that in normal situations (release build), init_connection_args can be omitted
    # otherwise, it should be set to the first H2O element in the pipeline.
    # Also note that in this specific case mixing numpy inputs with a fully H2O pipeline,
    # the last estimator requires the `data_conversion=True` param in order to return numpy arrays in predictions.
    pipeline = Pipeline([
        ('svd', H2OSVD(nv=3, transform='DESCALE', seed=seed, init_connection_args=init_connection_args)),
        ('estimator', H2OGradientBoostingClassifier(seed=seed, data_conversion=True))
    ])
    data = _get_data(format='numpy')
    assert isinstance(data.X_train, np.ndarray)
    pipeline.fit(data.X_train, data.y_train)
    preds = pipeline.predict(data.X_test)
    assert isinstance(preds, np.ndarray)
    assert preds.shape == (len(data.X_test),)
    pipeline.predict_proba(data.X_test)

    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = accuracy_score(data.y_test, preds)
    assert abs(score - skl_score) < 1e-6
    scores['h2o_only_pipeline_with_numpy_arrays'] = score


def test_mixed_pipeline_with_numpy_arrays():
    _ensure_connection_state(connected=False)
    # Note that in normal situations (release build), init_connection_args can be omitted
    # otherwise, it should be set to the first H2O element in the pipeline
    pipeline = Pipeline([
        ('svd', TruncatedSVD(n_components=3, random_state=seed)),
        ('estimator', H2OGradientBoostingClassifier(seed=seed, init_connection_args=init_connection_args))
    ])
    data = _get_data(format='numpy')
    assert isinstance(data.X_train, np.ndarray)
    pipeline.fit(data.X_train, data.y_train)
    preds = pipeline.predict(data.X_test)
    assert isinstance(preds, np.ndarray)
    assert preds.shape == (len(data.X_test),)
    pipeline.predict_proba(data.X_test)

    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = accuracy_score(data.y_test, preds)
    assert abs(score - skl_score) < 1e-6
    scores['mixed_pipeline_with_numpy_arrays'] = score


def test_generic_estimator_with_distribution_param():
    _ensure_connection_state(connected=False)
    # Note that in normal situations (release build), init_connection_args can be omitted
    # otherwise, it should be set to the first H2O element in the pipeline
    pipeline = Pipeline([
        ('svd', TruncatedSVD(n_components=3, random_state=seed)),
        ('estimator', H2OGradientBoostingEstimator(distribution='bernoulli', seed=seed, init_connection_args=init_connection_args))
    ])
    data = _get_data(format='numpy')
    assert isinstance(data.X_train, np.ndarray)
    pipeline.fit(data.X_train, data.y_train)
    preds = pipeline.predict(data.X_test)
    assert isinstance(preds, np.ndarray)
    assert preds.shape == (len(data.X_test),)
    pipeline.predict_proba(data.X_test)

    score = pipeline.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = accuracy_score(data.y_test, preds)
    assert abs(score - skl_score) < 1e-6
    scores['generic_estimator_with_distribution_param'] = score


def _assert_test_scores_equivalent(lk, rk):
    if lk in scores and rk in scores:
        assert abs(scores[lk] - abs(scores[rk])) < 1e-6, \
            "expected equivalent scores but got {lk}={lscore} and {rk}={rscore}"\
                .format(lk=lk, rk=rk, lscore=scores[lk], rscore=scores[rk])
    elif lk not in scores:
        print("no scores for {}".format(lk))
    else:
        print("no scores for {}".format(rk))


def test_scores_are_equivalent():
    _assert_test_scores_equivalent('h2o_only_pipeline_with_h2o_frame', 'h2o_only_pipeline_with_numpy_arrays')
    _assert_test_scores_equivalent('mixed_pipeline_with_numpy_arrays', 'generic_estimator_with_distribution_param')


pyunit_utils.run_tests([
    test_h2o_only_pipeline_with_h2o_frames,
    test_h2o_only_pipeline_with_numpy_arrays,
    test_mixed_pipeline_with_numpy_arrays,
    test_generic_estimator_with_distribution_param,
    test_scores_are_equivalent,
])
