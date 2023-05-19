import importlib, inspect, os, sys

import numpy as np
from sklearn.datasets import make_regression
from sklearn.decomposition import TruncatedSVD
from sklearn.metrics import r2_score, make_scorer
from sklearn.model_selection import train_test_split, RandomizedSearchCV
from sklearn.pipeline import Pipeline


import h2o
from h2o.cross_validation import H2OKFold
from h2o.model.regression import h2o_r2_score
from h2o.sklearn import h2o_connection, H2OGradientBoostingEstimator, H2OGradientBoostingRegressor, H2OSVD
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


def _get_data(format='numpy'):
    X, y = make_regression(n_samples=1000, n_features=10, n_informative=5, random_state=seed)
    X_train, X_test, y_train, y_test = train_test_split(X, y, random_state=seed)
    data = ns(X_train=X_train, X_test=X_test, y_train=y_train, y_test=y_test)
    if format == 'h2o':
        for k, v in data.__dict__.items():
            setattr(data, k, h2o.H2OFrame(v))
    return data


def test_h2o_only_pipeline_with_h2o_frames():
    pipeline = Pipeline([
        ('svd', H2OSVD(seed=seed)),
        ('estimator', H2OGradientBoostingRegressor(seed=seed))
    ])

    params = dict(
        svd__nv=[2, 3],
        svd__transform=['DESCALE', 'DEMEAN', 'NONE'],
        estimator__ntrees=[5, 10],
        estimator__max_depth=[1, 2, 3],
        estimator__learn_rate=[0.1, 0.2],
    )
    search = RandomizedSearchCV(pipeline,
                                params,
                                n_iter=5,
                                random_state=seed,
                                n_jobs=1,  # fails with parallel jobs
                                )
    data = _get_data(format='h2o')
    assert isinstance(data.X_train, h2o.H2OFrame)

    search.set_params(
        scoring=make_scorer(h2o_r2_score),
        cv=H2OKFold(data.X_train, n_folds=3, seed=seed),
    )

    search.fit(data.X_train, data.y_train)
    preds = search.predict(data.X_test)
    assert isinstance(preds, h2o.H2OFrame)
    assert preds.dim == [len(data.X_test), 1]

    score = search.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = r2_score(data.y_test.as_data_frame().values, preds.as_data_frame().values)
    assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)
    scores['h2o_only_pipeline_with_h2o_frame'] = score


def test_h2o_only_pipeline_with_numpy_arrays():
    # Note that in normal situations (release build), init_connection_args can be omitted
    # otherwise, it should be set to the first H2O element in the pipeline.
    # Also note that in this specific case mixing numpy inputs with a fully H2O pipeline,
    # the last estimator requires the `data_conversion=True` param in order to return numpy arrays in predictions.

    # Also, random search is never fitting on estimators but cloning them
    # therefore if we need to ensure a connection, we can:
    #  - use `h2o_connection` context manager: with h2o_connection(): ...
    #  - use the H2O estimator itself as a context manager: with H2OEstimator() as est: ...
    #  - init h2o manually
    with h2o_connection(**init_connection_args):
        pipeline = Pipeline([
            ('svd', H2OSVD(seed=seed, init_connection_args=init_connection_args)),
            ('estimator', H2OGradientBoostingRegressor(seed=seed, data_conversion=True)),
        ])

        params = dict(
            svd__nv=[2, 3],
            svd__transform=['DESCALE', 'DEMEAN', 'NONE'],
            estimator__ntrees=[5, 10],
            estimator__max_depth=[1, 2, 3],
            estimator__learn_rate=[0.1, 0.2],
        )
        search = RandomizedSearchCV(pipeline,
                                    params,
                                    n_iter=5,
                                    scoring='r2',
                                    cv=3,
                                    random_state=seed,
                                    n_jobs=1,
                                    )
        data = _get_data(format='numpy')
        assert isinstance(data.X_train, np.ndarray)

        search.fit(data.X_train, data.y_train)
        preds = search.predict(data.X_test)
        assert isinstance(preds, np.ndarray)
        assert preds.shape == (len(data.X_test),)

        score = search.score(data.X_test, data.y_test)
        assert isinstance(score, float)
        skl_score = r2_score(data.y_test, preds)
        assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)
        scores['h2o_only_pipeline_with_numpy_arrays'] = score


def test_mixed_pipeline_with_numpy_arrays():
    # Note that in normal situations (release build), init_connection_args can be omitted
    # otherwise, it should be set to the first H2O element in the pipeline
    with h2o_connection(**init_connection_args):
        pipeline = Pipeline([
            ('svd', TruncatedSVD(random_state=seed)),
            ('estimator', H2OGradientBoostingRegressor(seed=seed))
        ])

        params = dict(
            svd__n_components=[2, 3],
            estimator__ntrees=[5, 10],
            estimator__max_depth=[1, 2, 3],
            estimator__learn_rate=[0.1, 0.2],
        )
        search = RandomizedSearchCV(pipeline,
                                    params,
                                    n_iter=5,
                                    scoring='r2',
                                    cv=3,
                                    random_state=seed,
                                    n_jobs=1,
                                    )
        data = _get_data(format='numpy')
        assert isinstance(data.X_train, np.ndarray)

        search.fit(data.X_train, data.y_train)
        preds = search.predict(data.X_test)
        assert isinstance(preds, np.ndarray)
        assert preds.shape == (len(data.X_test),)

        score = search.score(data.X_test, data.y_test)
        assert isinstance(score, float)
        skl_score = r2_score(data.y_test, preds)
        assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)
        scores['mixed_pipeline_with_numpy_arrays'] = score


def _assert_test_scores_equivalent(lk, rk):
    if lk in scores and rk in scores:
        assert abs(scores[lk] - abs(scores[rk])) < 1e-6, \
            "expected equivalent scores but got {lk}={lscore} and {rk}={rscore}" \
                .format(lk=lk, rk=rk, lscore=scores[lk], rscore=scores[rk])
    elif lk not in scores:
        print("no scores for {}".format(lk))
    else:
        print("no scores for {}".format(rk))


def test_scores_are_equivalent():
    _assert_test_scores_equivalent('h2o_only_pipeline_with_h2o_frame', 'h2o_only_pipeline_with_numpy_arrays')


pyunit_utils.run_tests([
    test_h2o_only_pipeline_with_h2o_frames,
    test_h2o_only_pipeline_with_numpy_arrays,
    test_mixed_pipeline_with_numpy_arrays,
    test_scores_are_equivalent,
])
