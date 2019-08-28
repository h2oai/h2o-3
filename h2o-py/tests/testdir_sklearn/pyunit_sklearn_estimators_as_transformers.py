from __future__ import print_function
from collections import defaultdict
from functools import partial
import gc, inspect, os, sys

import numpy as np
from sklearn.datasets import make_classification, make_regression
from sklearn.metrics import accuracy_score
from sklearn.model_selection import train_test_split

import h2o
from h2o.sklearn.wrapper import H2OConnectionMonitorMixin


sys.path.insert(1, os.path.join("..",".."))
from tests import pyunit_utils, Namespace as ns


"""
This test suite creates a default sklearn classification estimator for each H2O estimator.
Then, it feeds them with H2O frames (more efficient and ensures compatibility with old API.)
or with numpy arrays to provide the simplest approach for users wanting to use H2O like any sklearn estimator.
"""

seed = 2019
init_connection_args = dict(strict_version_check=False, show_progress=True)

results = defaultdict(dict)


def _get_data(format='numpy', n_classes=2):
    generator = make_classification if n_classes > 0 else make_regression
    params = dict(n_samples=100, n_features=5, n_informative=n_classes or 2, n_repeated=0, random_state=seed)
    if generator is make_classification:
        params.update(n_classes=n_classes)

    X, y = generator(**params)
    X_train, X_test, y_train, y_test = train_test_split(X, y, random_state=seed, train_size=0.75)
    data = ns(X_train=X_train, X_test=X_test, y_train=y_train, y_test=y_test)
    if format == 'h2o':
        for k, v in data.__dict__.items():
            setattr(data, k, h2o.H2OFrame(v))
    return data


def _get_default_args(estimator_cls):
    defaults = dict(
        H2OAggregatorEstimator=dict(),
        H2OGeneralizedLowRankEstimator=dict(k=2, seed=seed),
        H2OPrincipalComponentAnalysisEstimator=dict(k=2, seed=seed),
        H2OSingularValueDecompositionEstimator=dict(nv=2, seed=seed),
    )
    return defaults.get(estimator_cls.__name__, dict(seed=seed))


def _get_custom_behaviour(estimator_cls):
    train_size = 100 * 0.75  # cf. _get_data
    custom = dict(
        H2OAggregatorEstimator=dict(predict=False, result_shape=(train_size, 5+1)),
        H2OGeneralizedLowRankEstimator=dict(results_may_differ=True,
                                            idempotent=False,
                                            result_shape=(train_size, 5)),  # (n_samples, n_features)
        H2OPrincipalComponentAnalysisEstimator=dict(result_shape=(train_size, 2)),  # (n_samples, k)
        H2OSingularValueDecompositionEstimator=dict(result_shape=(train_size, 2)),  # (n_samples, nv)

    )
    return custom.get(estimator_cls.__name__, dict())


def test_estimator_with_h2o_frames(estimator_cls):
    estimator = estimator_cls(init_connection_args=init_connection_args, **_get_default_args(estimator_cls))

    data = _get_data(format='h2o', n_classes=3)
    assert isinstance(data.X_train, h2o.H2OFrame)

    estimator.fit(data.X_train)
    # estimator.show()
    res = estimator.transform(data.X_train)
    print(res)
    assert isinstance(res, h2o.H2OFrame)
    assert res.dim == list(_get_custom_behaviour(estimator_cls).get('result_shape'))
    results[estimator_cls].update(with_h2o_frames=res.as_data_frame().values)

    res_ft = estimator.fit_transform(data.X_train)
    print(res)
    assert isinstance(res, h2o.H2OFrame)
    assert np.allclose(res.as_data_frame().values, res_ft.as_data_frame().values)


def test_estimator_with_numpy_arrays(estimator_cls):
    estimator = estimator_cls(init_connection_args=init_connection_args, **_get_default_args(estimator_cls))

    data = _get_data(format='numpy', n_classes=3)
    assert isinstance(data.X_train, np.ndarray)

    with estimator:
        estimator.fit(data.X_train)
        # estimator.show()
        res = estimator.transform(data.X_train)
        print(res)
        assert isinstance(res, h2o.H2OFrame)
        assert res.dim == list(_get_custom_behaviour(estimator_cls).get('result_shape'))
        results[estimator_cls].update(with_numpy_arrays_in=res.as_data_frame().values)

        if _get_custom_behaviour(estimator_cls).get('predict', True):
            res_pred = estimator.predict(data.X_train)
            print(res_pred[:10])
            assert isinstance(res_pred, np.ndarray)
            assert np.allclose(res.as_data_frame().values, res_pred)

        res_ft = estimator.fit_transform(data.X_train)
        print(res_ft)
        assert isinstance(res_ft, h2o.H2OFrame)
        try:
            assert np.allclose(res.as_data_frame().values, res_ft.as_data_frame().values)
        except AssertionError as e:
            if not _get_custom_behaviour(estimator_cls).get('idempotent', True):
                print("ERROR !!! Due to lack of idempotence, "
                      "{} gives different results on fit+transform and fit_transform with numpy arrays:"
                      .format(estimator_cls.__name__)+str(e))
            else:
                raise e


def test_estimator_with_numpy_arrays_as_result(estimator_cls):
    estimator = estimator_cls(data_conversion=True, init_connection_args=init_connection_args, **_get_default_args(estimator_cls))

    data = _get_data(format='numpy', n_classes=3)
    assert isinstance(data.X_train, np.ndarray)

    with estimator:
        estimator.fit(data.X_train)
        # estimator.show()
        res = estimator.transform(data.X_train)
        print(res[:10])
        assert isinstance(res, np.ndarray)
        assert res.shape == _get_custom_behaviour(estimator_cls).get('result_shape')
        results[estimator_cls].update(with_numpy_arrays_inout=res)

        res_ft = estimator.fit_transform(data.X_train)
        print(res[:10])
        assert isinstance(res_ft, np.ndarray)
        try:
            assert np.allclose(res, res_ft)
        except AssertionError as e:
            if not _get_custom_behaviour(estimator_cls).get('idempotent', True):
                print("ERROR !!! Due to lack of idempotence, "
                      "{} gives different results on fit+transform and fit_transform with numpy arrays:"
                      .format(estimator_cls.__name__)+str(e))
            else:
                raise e

        if _get_custom_behaviour(estimator_cls).get('predict', True):
            res_fp = estimator.fit_predict(data.X_train)
            assert isinstance(res_fp, np.ndarray)
            assert np.allclose(res_ft, res_fp)


def test_results_are_equivalent(estimator_cls):
    comparisons = [('with_h2o_frames', 'with_numpy_arrays_in'),
                   ('with_numpy_arrays_in', 'with_numpy_arrays_inout')]
    est_results = results[estimator_cls]
    for lk, rk in comparisons:
        if lk in est_results and rk in est_results:
            try:
                assert np.allclose(est_results[lk], est_results[rk]), \
                    "expected equivalent results but got {lk}={lresult} and {rk}={rresult}" \
                    .format(lk=lk, rk=rk, lresult=est_results[lk], rresult=est_results[rk])
            except AssertionError as e:
                if _get_custom_behaviour(estimator_cls).get('results_may_differ', False):
                    print("ERROR !!! "+str(e))
                else:
                    raise e
        elif lk not in est_results:
            print("no results for {}".format(estimator_cls.__name__+' '+lk))
        else:
            print("no results for {}".format(estimator_cls.__name__+' '+rk))


def make_test(test, transformer):
    bound_test = partial(test, transformer)
    bound_test.__name__ = test.__name__
    pyunit_utils.tag_test(bound_test, transformer.__name__)
    return bound_test


def make_tests(transformer):
    return list(map(lambda test: make_test(test, transformer), [
        test_estimator_with_h2o_frames,
        test_estimator_with_numpy_arrays,
        test_estimator_with_numpy_arrays_as_result,
        test_results_are_equivalent
    ]))


transformers = [
    'H2OAggregatorEstimator',
    'H2OGeneralizedLowRankEstimator',
    'H2OPrincipalComponentAnalysisEstimator',
    'H2OSingularValueDecompositionEstimator'
]
estimators = [cls for name, cls in inspect.getmembers(h2o.sklearn, inspect.isclass) if name in transformers]
pyunit_utils.run_tests([make_tests(c) for c in estimators])
