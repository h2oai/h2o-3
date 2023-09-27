from collections import defaultdict
from functools import partial
import gc, inspect, os, sys

import numpy as np
from sklearn.datasets import make_regression
from sklearn.metrics import r2_score
from sklearn.model_selection import train_test_split

import h2o
from h2o.sklearn.wrapper import H2OConnectionMonitorMixin


sys.path.insert(1, os.path.join("..",".."))
from tests import pyunit_utils, Namespace as ns


"""
This test suite creates a default sklearn regression estimator for each H2O estimator.
Then, it feeds them with H2O frames (more efficient and ensures compatibility with old API.)
or with numpy arrays to provide the simplest approach for users wanting to use H2O like any sklearn estimator.
"""

seed = 2019
init_connection_args = dict(strict_version_check=False, show_progress=True)

scores = defaultdict(dict)


def _get_data(format='numpy'):
    X, y = make_regression(n_samples=100, n_features=10, n_informative=5, random_state=seed)
    X_train, X_test, y_train, y_test = train_test_split(X, y, random_state=seed)
    data = ns(X_train=X_train, X_test=X_test, y_train=y_train, y_test=y_test)
    if format == 'h2o':
        for k, v in data.__dict__.items():
            setattr(data, k, h2o.H2OFrame(v))
    return data


def _get_default_args(estimator_cls):
    defaults = dict(
        H2OCoxProportionalHazardsRegressor=dict(),
        H2ODeepLearningRegressor=dict(seed=seed, reproducible=True),
        H2OGeneralizedAdditiveRegressor=dict(family='gaussian', seed=seed, gam_columns=["C1"]),
        H2OGeneralizedLinearRegressor=dict(family='gaussian', seed=seed),
    )
    return defaults.get(estimator_cls.__name__, dict(seed=seed))


def _get_custom_behaviour(estimator_cls):
    custom = dict(
        # H2ODeepLearningRegressor=dict(scores_may_differ=True),
    )
    return custom.get(estimator_cls.__name__, dict())



def test_estimator_with_h2o_frames(estimator_cls):
    args = _get_default_args(estimator_cls)
    estimator = estimator_cls(**args)

    data = _get_data(format='h2o')
    assert isinstance(data.X_train, h2o.H2OFrame)
    estimator.fit(data.X_train, data.y_train)
    preds = estimator.predict(data.X_test)
    print(preds)
    assert isinstance(preds, h2o.H2OFrame)
    if _get_custom_behaviour(estimator_cls).get('preds_as_vector', True):
        assert preds.dim == [len(data.X_test), 1], "got {}".format(preds.dim)
    else:
        assert preds.dim[0] == len(data.X_test)

    score = estimator.score(data.X_test, data.y_test)
    assert isinstance(score, float)
    skl_score = r2_score(data.y_test.as_data_frame().values, preds.as_data_frame().values)
    assert abs(score - skl_score) < 1e-6, "score={}, skl_score={}".format(score, skl_score)
    scores[estimator_cls].update(with_h2o_frames=score)


def test_estimator_with_numpy_arrays(estimator_cls):
    estimator = estimator_cls(init_connection_args=init_connection_args, **_get_default_args(estimator_cls))

    data = _get_data(format='numpy')
    assert isinstance(data.X_train, np.ndarray)

    with estimator:
        estimator.fit(data.X_train, data.y_train)
        preds = estimator.predict(data.X_test)
        print(preds)
        assert isinstance(preds, np.ndarray)
        if _get_custom_behaviour(estimator_cls).get('preds_as_vector', True):
            assert preds.shape == (len(data.X_test),), "got {}".format(preds.shape)
        else:
            assert preds.shape[0] == len(data.X_test)

        score = estimator.score(data.X_test, data.y_test)
        assert isinstance(score, float)
        skl_score = r2_score(data.y_test, preds)
        assert abs(score - skl_score) < 1e-6
        scores[estimator_cls].update(with_numpy_arrays=score)


def test_scores_are_equivalent(estimator_cls):
    try:
        lk, rk = ('with_h2o_frames', 'with_numpy_arrays')
        est_scores = scores[estimator_cls]
        if lk in est_scores and rk in est_scores:
            assert abs(est_scores[lk] - est_scores[rk]) < 1e-6, \
                "expected equivalent scores but got {lk}={lscore} and {rk}={rscore}" \
                    .format(lk=lk, rk=rk, lscore=est_scores[lk], rscore=est_scores[rk])
        elif lk not in est_scores:
            print("no scores for {}".format(estimator_cls.__name__+' '+lk))
        else:
            print("no scores for {}".format(estimator_cls.__name__+' '+rk))
    except AssertionError as e:
        if _get_custom_behaviour(estimator_cls).get('scores_may_differ', False):
            print("ERROR !!! "+str(e))
        else:
            raise e


def make_test(test, classifier):
    bound_test = partial(test, classifier)
    bound_test.__name__ = test.__name__
    pyunit_utils.tag_test(bound_test, classifier.__name__)
    return bound_test


def make_tests(classifier):
    return list(map(lambda test: make_test(test, classifier), [
        test_estimator_with_h2o_frames,
        test_estimator_with_numpy_arrays,
        test_scores_are_equivalent
    ]))


failing = [
    'H2OCoxProportionalHazardsRegressor',  # doesn't support regression?
    'H2OStackedEnsembleRegressor',  # needs a separate test (requires models as parameters),
    'H2OUpliftRandomForestRegressor',  # does not support regression yet
    'H2ODecisionTreeRegressor',  # does not support regression yet
    'H2OAdaBoostRegressor'  # does not support regression yet
]
regressors = [cls for name, cls in inspect.getmembers(h2o.sklearn, inspect.isclass)
              if name.endswith('Regressor') and name not in ['H2OAutoMLRegressor']+failing]
pyunit_utils.run_tests([make_tests(c) for c in regressors])
