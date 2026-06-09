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

import sklearn

from h2o.sklearn.wrapper import (
    BaseSklearnEstimator,
    BaseEstimatorMixin,
    H2OConnectionMonitorMixin,
)


SKLEARN_VERSION = tuple(int(p) for p in sklearn.__version__.split(".")[:2])


class _Skip(Exception):
    """Sentinel raised by a test to signal a version-conditional skip."""


def _assert_raises(exc_type, fn):
    try:
        fn()
    except exc_type as e:
        return e
    except Exception as e:
        raise AssertionError(
            "expected %s, got %s: %s" % (exc_type.__name__, type(e).__name__, e)
        )
    raise AssertionError("expected %s, no exception raised" % exc_type.__name__)


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
    _assert_raises(AttributeError, lambda: w.classes_)


def test_classes_raises_for_unfitted_classifier():
    w = _make_wrapper("classifier", domains=None)
    # NotFittedError on sklearn ≥ 0.22; RuntimeError fallback otherwise
    exc = _assert_raises(Exception, lambda: w.classes_)
    assert "not fitted" in str(exc).lower()


def test_hasattr_classes_is_false_for_regressor():
    """sklearn 1.6+ scoring calls hasattr(est, 'classes_') — must return False on regressor."""
    w = _make_wrapper("regressor", domains=[None, [None]])
    assert not hasattr(w, "classes_")


# ---------- __sklearn_tags__ -------------------------------------------------

def test_classifier_tags_populated_on_sklearn_16_plus():
    if SKLEARN_VERSION < (1, 6):
        raise _Skip("__sklearn_tags__ only meaningful on sklearn >= 1.6")
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
        raise _Skip("__sklearn_tags__ only meaningful on sklearn >= 1.6")
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
        raise _Skip("legacy sklearn lacks __sklearn_tags__ on BaseEstimator")
    w = _make_wrapper("classifier", domains=[None, ["A", "B"]])
    _assert_raises(AttributeError, lambda: w.__sklearn_tags__())


# ---------- P0-3 / P1-20: MRO fallback in __sklearn_tags__ --------------------

def _make_wrapper_no_estimator_type_with_mixin(mixin_class, domains):
    """Construct a wrapper that has a Classifier/RegressorMixin in MRO but no
    explicit ``_estimator_type``. This exercises the MRO-fallback branch added
    for sklearn 1.8 (which removed ``_estimator_type`` from ClassifierMixin).
    """
    class _ConcreteMixinWrapper(BaseSklearnEstimator, mixin_class):
        # NO _estimator_type attribute defined here on purpose.
        _classifier_distributions = ()

        def __init__(self):
            self._estimator = _FakeFittedEstimator(domains=domains)
            self._estimator_cls = None
            self._custom_params = {}

        def _is_classifier_distribution(self):
            return False

        def _is_regressor_distribution(self):
            return False

    # Drop any class-level _estimator_type the mixin may set on sklearn < 1.8.
    if "_estimator_type" in _ConcreteMixinWrapper.__dict__:
        delattr(_ConcreteMixinWrapper, "_estimator_type")
    return _ConcreteMixinWrapper()


def test_classifier_tags_via_mro_fallback():
    """sklearn 1.8 removed `_estimator_type = 'classifier'` from ClassifierMixin.
    With `_estimator_type` unset and the mixin in MRO, the wrapper must still
    resolve as a classifier through the MRO-fallback branch in __sklearn_tags__.
    """
    if SKLEARN_VERSION < (1, 6):
        raise _Skip("__sklearn_tags__ only meaningful on sklearn >= 1.6")
    from sklearn.base import ClassifierMixin
    w = _make_wrapper_no_estimator_type_with_mixin(ClassifierMixin, domains=[None, ["A", "B"]])
    # Remove any inherited _estimator_type so the MRO branch is forced.
    try:
        del w._estimator_type
    except AttributeError:
        pass
    if "_estimator_type" in type(w).__dict__:
        delattr(type(w), "_estimator_type")
    tags = w.__sklearn_tags__()
    assert tags.estimator_type == "classifier", \
        "MRO fallback should detect ClassifierMixin and stamp estimator_type='classifier'"


def test_regressor_tags_via_mro_fallback():
    if SKLEARN_VERSION < (1, 6):
        raise _Skip("__sklearn_tags__ only meaningful on sklearn >= 1.6")
    from sklearn.base import RegressorMixin
    w = _make_wrapper_no_estimator_type_with_mixin(RegressorMixin, domains=[None, [None]])
    try:
        del w._estimator_type
    except AttributeError:
        pass
    if "_estimator_type" in type(w).__dict__:
        delattr(type(w), "_estimator_type")
    tags = w.__sklearn_tags__()
    assert tags.estimator_type == "regressor"


# ---------- P0-3: distribution-based fallback in __sklearn_tags__ ------------

def _make_generic_wrapper_with_distribution(distribution, domains):
    """Generic wrapper (no `_estimator_type`, no ClassifierMixin/RegressorMixin
    in MRO) that derives classifier-ness from the H2O `distribution=` kwarg.
    """
    class _GenericDistWrapper(BaseSklearnEstimator):
        # NO _estimator_type and no typed mixin in MRO.
        _classifier_distributions = (
            'bernoulli', 'binomial', 'quasibinomial', 'multinomial'
        )

        def __init__(self, distribution):
            self._estimator = _FakeFittedEstimator(domains=domains)
            self._estimator_cls = None
            self._custom_params = {}
            self.distribution = distribution

        def _get_custom_param(self, name, default=None):
            return self._custom_params.get(name, default)

        def _is_classifier_distribution(self):
            return self.distribution in self._classifier_distributions

        def _is_regressor_distribution(self):
            return self.distribution not in (None,) + self._classifier_distributions

    return _GenericDistWrapper(distribution=distribution)


def test_classifier_tags_via_distribution_fallback():
    """Generic wrappers built via make_estimator(type='estimator') have neither
    `_estimator_type` nor a typed Mixin in MRO. They derive type from the H2O
    `distribution=` param. Without the distribution fallback, sklearn 1.6+'s
    `is_classifier` reads False from tags while `BaseEstimatorMixin.is_classifier`
    reads True — Pipeline/GridSearchCV scoring silently picks regression metrics.
    """
    if SKLEARN_VERSION < (1, 6):
        raise _Skip("__sklearn_tags__ only meaningful on sklearn >= 1.6")
    w = _make_generic_wrapper_with_distribution("bernoulli", domains=[None, ["A", "B"]])
    tags = w.__sklearn_tags__()
    assert tags.estimator_type == "classifier", \
        "Distribution-based fallback should detect bernoulli/binomial/etc. as classifier"


def test_regressor_tags_via_distribution_fallback():
    if SKLEARN_VERSION < (1, 6):
        raise _Skip("__sklearn_tags__ only meaningful on sklearn >= 1.6")
    w = _make_generic_wrapper_with_distribution("gaussian", domains=[None, [None]])
    tags = w.__sklearn_tags__()
    assert tags.estimator_type == "regressor"


# ---------- P1-1 / P1-21: AutoML _leader_id signal ---------------------------

class _FakeAutoML(object):
    """Stand-in for H2OAutoML — _leader_id is populated only on successful fit."""

    def __init__(self, leader_id=None, leader_domains=None):
        self._model_json = None  # AutoML wrapper has no _model_json of its own
        self._leader_id = leader_id
        # Mock the `leader` @property as a lazy attribute. We do NOT touch it
        # from __sklearn_is_fitted__ (intentionally: reading `leader` triggers
        # a server round-trip on every check_is_fitted call).
        self._leader = _FakeFittedEstimator(domains=leader_domains) if leader_id else None

    @property
    def leader(self):  # pragma: no cover — must NOT be called from __sklearn_is_fitted__
        raise AssertionError(
            "__sklearn_is_fitted__ must use _leader_id, not the leader property "
            "(reading `leader` is a server round-trip on every fit check)"
        )


def _make_automl_wrapper(automl):
    class _AutoMLWrapper(BaseSklearnEstimator):
        _estimator_type = "classifier"
        _classifier_distributions = ()

        def __init__(self):
            self._estimator = automl
            self._estimator_cls = None
            self._custom_params = {}

        def is_classifier(self):
            return True

        def is_regressor(self):
            return False

    return _AutoMLWrapper()


def test_is_fitted_true_when_automl_leader_id_present():
    """AutoML fit signal: _leader_id set => __sklearn_is_fitted__ True."""
    w = _make_automl_wrapper(_FakeAutoML(leader_id="some_model_id"))
    assert w.__sklearn_is_fitted__() is True


def test_is_fitted_false_when_automl_not_trained():
    """AutoML wrapper with no leader yet => __sklearn_is_fitted__ False."""
    w = _make_automl_wrapper(_FakeAutoML(leader_id=None))
    assert w.__sklearn_is_fitted__() is False


def test_is_fitted_does_not_read_leader_property():
    """Regression guard: reading `automl.leader` is a server round-trip;
    __sklearn_is_fitted__ must use `_leader_id` instead. _FakeAutoML.leader
    raises AssertionError if accessed — test passes iff it is never read.
    """
    w = _make_automl_wrapper(_FakeAutoML(leader_id="some_model_id"))
    for _ in range(5):
        assert w.__sklearn_is_fitted__() is True  # would AssertionError on round-trip


if __name__ == "__main__":
    failed = []
    for name, fn in list(globals().items()):
        if not name.startswith("test_") or not callable(fn):
            continue
        try:
            fn()
            print("PASS:", name)
        except _Skip as e:
            print("SKIP:", name, "—", e)
        except Exception as e:
            failed.append((name, e))
            print("FAIL:", name, "—", type(e).__name__, e)
    if failed:
        sys.exit(1)
    print("All tests passed.")
