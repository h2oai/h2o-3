#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
Smoke tests for ``BaseSklearnEstimator.__sklearn_tags__`` / ``classes_`` /
``__sklearn_is_fitted__`` against whatever sklearn version is installed.

CI pins sklearn ``1.0.2`` (Py3.8-3.10) and ``1.8.0`` (Py3.11+); 1.6 and 1.7
are not pinned but they ARE the versions where the tag-dataclass machinery was
introduced (1.6) and where ``_estimator_type`` was removed from
``ClassifierMixin`` (1.8). These tests guard against quietly broken behaviour
on whichever sklearn the user has — run them locally with each release of
interest (``pip install scikit-learn==1.6.0`` etc.) to verify cross-version
compatibility.

The tests do NOT require an H2O server connection: a duck-typed fake
``_estimator`` provides the minimum surface area the wrapper inspects.
"""
import sys

import pytest

import sklearn

from h2o.sklearn.wrapper import (
    BaseSklearnEstimator,
    BaseEstimatorMixin,
    H2OConnectionMonitorMixin,
)


SKLEARN_VERSION = tuple(int(p) for p in sklearn.__version__.split(".")[:2])


class _FakeFittedEstimator(object):
    """Stand-in for the H2O server-bound estimator that lives on ``_estimator``."""

    def __init__(self, domains=None):
        self._model_json = {"output": {"domains": domains}} if domains is not None else None


def _make_wrapper(estimator_type, domains):
    """Construct a concrete BaseSklearnEstimator subclass for the given type."""

    class _Concrete(BaseSklearnEstimator):
        # Match BaseEstimatorMixin/H2OConnectionMonitor expectations the bare minimum.
        _estimator_type = estimator_type
        _classifier_distributions = ()

        def __init__(self):
            # Skip BaseSklearnEstimator.__init__ entirely — it expects the H2O
            # estimator factory. We just stamp the attributes the tag/classes
            # path needs.
            self._estimator = _FakeFittedEstimator(domains=domains)
            self._estimator_cls = None
            self._custom_params = {}

        def is_classifier(self):
            return estimator_type == "classifier"

        def is_regressor(self):
            return estimator_type == "regressor"

        def _is_classifier_distribution(self):
            return False

        def _is_regressor_distribution(self):
            return False

    return _Concrete()


# ---------- __sklearn_is_fitted__ --------------------------------------------

def test_is_fitted_true_when_model_json_present():
    w = _make_wrapper("classifier", domains=[None, None, ["A", "B"]])
    assert w.__sklearn_is_fitted__() is True


def test_is_fitted_false_when_no_model_json():
    w = _make_wrapper("classifier", domains=None)
    assert w.__sklearn_is_fitted__() is False


def test_is_fitted_false_when_estimator_missing():
    w = _make_wrapper("classifier", domains=None)
    w._estimator = None
    assert w.__sklearn_is_fitted__() is False


# ---------- classes_ ----------------------------------------------------------

def test_classes_returns_domain_for_classifier():
    w = _make_wrapper("classifier", domains=[None, ["A", "B", "C"]])
    out = w.classes_
    assert list(out) == ["A", "B", "C"]


def test_classes_canonical_int_domain_is_coerced():
    w = _make_wrapper("classifier", domains=[None, ["0", "1"]])
    out = w.classes_
    assert list(out) == [0, 1]


def test_classes_raises_attributeerror_for_regressor():
    w = _make_wrapper("regressor", domains=[None, [None]])
    with pytest.raises(AttributeError):
        _ = w.classes_


def test_classes_raises_for_unfitted_classifier():
    w = _make_wrapper("classifier", domains=None)
    # NotFittedError on sklearn ≥ 0.22; RuntimeError fallback otherwise
    with pytest.raises(Exception) as exc:
        _ = w.classes_
    assert "not fitted" in str(exc.value).lower()


def test_hasattr_classes_is_false_for_regressor():
    """sklearn 1.6+ scoring calls hasattr(est, 'classes_') — must return False on regressor."""
    w = _make_wrapper("regressor", domains=[None, [None]])
    assert not hasattr(w, "classes_")


# ---------- __sklearn_tags__ -------------------------------------------------

def test_classifier_tags_populated_on_sklearn_16_plus():
    if SKLEARN_VERSION < (1, 6):
        pytest.skip("__sklearn_tags__ only meaningful on sklearn ≥ 1.6")
    w = _make_wrapper("classifier", domains=[None, ["A", "B"]])
    tags = w.__sklearn_tags__()
    assert tags.estimator_type == "classifier"
    assert getattr(tags, "classifier_tags", None) is not None, \
        "ClassifierTags dataclass should be populated"
    target_tags = getattr(tags, "target_tags", None)
    if target_tags is not None and hasattr(target_tags, "required"):
        assert target_tags.required is True


def test_regressor_tags_populated_on_sklearn_16_plus():
    if SKLEARN_VERSION < (1, 6):
        pytest.skip("__sklearn_tags__ only meaningful on sklearn ≥ 1.6")
    w = _make_wrapper("regressor", domains=[None, [None]])
    tags = w.__sklearn_tags__()
    assert tags.estimator_type == "regressor"
    assert getattr(tags, "regressor_tags", None) is not None
    target_tags = getattr(tags, "target_tags", None)
    if target_tags is not None and hasattr(target_tags, "required"):
        assert target_tags.required is True


def test_sklearn_tags_raises_attributeerror_on_legacy_sklearn():
    """For sklearn < 1.6 the wrapper must raise AttributeError so hasattr returns False."""
    if SKLEARN_VERSION >= (1, 6):
        pytest.skip("legacy sklearn lacks __sklearn_tags__ on BaseEstimator")
    w = _make_wrapper("classifier", domains=[None, ["A", "B"]])
    with pytest.raises(AttributeError):
        w.__sklearn_tags__()


if __name__ == "__main__":
    # Direct invocation prints results in the same style as the other pyunits;
    # pytest invocation handles the skipif marks automatically.
    failed = []
    for name, fn in list(globals().items()):
        if not name.startswith("test_") or not callable(fn):
            continue
        try:
            fn()
            print("PASS:", name)
        except pytest.skip.Exception as e:
            print("SKIP:", name, "—", e)
        except Exception as e:
            failed.append((name, e))
            print("FAIL:", name, "—", type(e).__name__, e)
    if failed:
        sys.exit(1)
    print("All tests passed.")
