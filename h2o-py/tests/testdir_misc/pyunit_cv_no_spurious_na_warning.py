#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
Regression tests for h2o.cross_validation helpers:

  * ``_materialize_fold_column`` must not emit the spurious
    ``_warn_as_data_frame_na_default`` FutureWarning on every CV iteration
    (P0-3).
  * ``__iter__`` must preserve the historical ``(train_mask, test_mask)``
    H2OFrame-mask contract (no API break), while the scikit-learn splitter API
    ``split`` / ``get_n_splits`` provides the integer-index path scikit-learn
    >= 1.6 requires.
  * ``H2OPartitionIterator.iter_h2oframes`` must keep the split server-side
    (P1-3) — no driver-side materialization of fold indices.

Tests run without an H2O server connection by duck-typing the H2OFrame and
fold-column objects.
"""
import warnings

import numpy as np
import pandas as pd

import h2o.cross_validation as _cv
from h2o.cross_validation import (
    _materialize_fold_column,
    _maybe_warn_large_frame,
    _LARGE_FRAME_THRESHOLD,
    H2OKFold,
    H2OStratifiedKFold,
)


def _reset_cv_warning_state():
    """Clear cross_validation's warning registry so each test re-observes the
    one-shot warnings. The module now relies on the warnings module's own
    per-(message, category, lineno) dedup instead of hand-rolled module latches,
    so isolation means dropping the module's __warningregistry__."""
    _cv.__dict__.pop("__warningregistry__", None)


class _FakeFoldColumn(object):
    """Minimal duck type matching what ``_materialize_fold_column`` consumes."""

    def __init__(self, fold_values=(0, 1, 2, 0, 1, 2)):
        self.columns = ["fold_id"]
        self.as_data_frame_calls = []
        self._fold_values = list(fold_values)
        # __getitem__ access support for iter_h2oframes mask construction
        self._compare_calls = []

    def as_data_frame(self, **kwargs):
        self.as_data_frame_calls.append(kwargs)
        return pd.DataFrame({"fold_id": self._fold_values})

    def __getitem__(self, key):
        return _FakeMaskedComparison(self, key)


class _FakeMask(object):
    """Server-side boolean-mask stand-in. Supports ``1 - mask`` (the train-mask
    idiom in ``__iter__``) so the historical mask contract can be exercised offline."""

    def __init__(self, kind, fold):
        self.kind = kind   # "eq" (test), "ne", or "complement" (train via 1 - mask)
        self.fold = fold

    def __rsub__(self, other):  # 1 - mask  -> complementary (train) mask
        return _FakeMask("complement", self.fold)


class _FakeMaskedComparison(object):
    """Returned by ``fold_col[colname]`` — records ``== fold`` and ``!= fold``."""

    def __init__(self, parent, col):
        self.parent = parent
        self.col = col

    def __eq__(self, other):
        self.parent._compare_calls.append(("eq", other))
        return _FakeMask("eq", other)

    def __ne__(self, other):
        self.parent._compare_calls.append(("ne", other))
        return _FakeMask("ne", other)


class _FakeH2OFrame(object):
    """Stand-in H2OFrame that records the masks used to slice it."""

    def __init__(self, n=6):
        self._n = n
        self._kfold_calls = []
        self.slice_calls = []

    def __len__(self):
        return self._n

    def kfold_column(self, n_folds, seed):
        self._kfold_calls.append((n_folds, seed))
        # Round-robin fold ids matching len(self)
        return _FakeFoldColumn([i % n_folds for i in range(self._n)])

    def stratified_kfold_column(self, n_folds, seed):
        self._kfold_calls.append((n_folds, seed))
        return _FakeFoldColumn([i % n_folds for i in range(self._n)])

    def __getitem__(self, idx):
        # Record exactly the mask object used; iter_h2oframes must pass
        # server-side mask tuples, not numpy arrays.
        self.slice_calls.append(idx)
        # Return ourselves to keep the iterator happy.
        return self


# ---------- P0-3 -------------------------------------------------------------

def test_materialize_fold_column_passes_explicit_na_values():
    fake = _FakeFoldColumn()
    _materialize_fold_column(fake)
    assert len(fake.as_data_frame_calls) == 1
    call = fake.as_data_frame_calls[0]
    assert "na_values" in call, \
        "as_data_frame must be called with na_values= to suppress the FutureWarning"
    assert call["na_values"] == [""]


def test_materialize_fold_column_returns_int32_array():
    fake = _FakeFoldColumn()
    arr = _materialize_fold_column(fake)
    assert isinstance(arr, np.ndarray)
    assert arr.dtype == np.int32
    assert list(arr) == [0, 1, 2, 0, 1, 2]


def test_materialize_fold_column_does_not_emit_future_warning():
    fake = _FakeFoldColumn()
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        _materialize_fold_column(fake)
    future_warnings = [w for w in caught if issubclass(w.category, FutureWarning)]
    assert future_warnings == [], \
        "Got unexpected FutureWarning(s): %r" % ([str(w.message) for w in future_warnings],)


# ---------- __iter__ preserves the historical H2OFrame-mask contract ---------

def test_iter_yields_h2oframe_masks_not_indices():
    """``__iter__`` must keep yielding ``(train_mask, test_mask)`` server-side masks
    (the historical contract) — NOT numpy integer arrays. The integer-index path
    moved to split() so the public iteration contract is unchanged (no API break)."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    pairs = list(iter(kf))
    assert len(pairs) == 3, "expected n_folds=3 (train, test) pairs; got %d" % (len(pairs),)
    for train_mask, test_mask in pairs:
        assert isinstance(train_mask, _FakeMask) and isinstance(test_mask, _FakeMask), \
            "__iter__ must yield H2OFrame masks, not %r/%r" % (type(train_mask), type(test_mask))
        assert test_mask.kind == "eq", "test mask should be (fold_col == k)"
        assert train_mask.kind == "complement", "train mask should be (1 - test_mask)"


def test_iter_does_not_emit_any_warning():
    """Restoring the historical mask contract means plain iteration no longer warns
    about a contract change (there is none) and does not materialize on the driver."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    fake_fold_col = _FakeFoldColumn()
    kf._fold_column = fake_fold_col
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        list(iter(kf))
    assert caught == [], "iter() must not warn; got %r" % ([str(w.message) for w in caught],)
    assert fake_fold_col.as_data_frame_calls == [], \
        "iter() must stay server-side (no as_data_frame materialization)"


# ---------- P0-2: H2OStratifiedKFold.iter_h2oframes() contract ---------------

def test_stratified_iter_h2oframes_raises_without_feature_frame():
    """Pre-3.46.0.12, iter_h2oframes() silently yielded slices of y only — broken for
    the documented sklearn-compat migration path. New behavior: raise loudly unless
    the caller passed fr= at construction time.
    """
    _reset_cv_warning_state()
    y = _FakeH2OFrame(n=6)
    skf = H2OStratifiedKFold(y, n_folds=3, seed=42)
    skf._fold_column = _FakeFoldColumn()
    raised = False
    try:
        list(skf.iter_h2oframes())
    except NotImplementedError as exc:
        raised = True
        assert "feature frame" in str(exc), "Error must explain the missing arg"
    assert raised, "Expected NotImplementedError when fr= is not provided"


def test_stratified_iter_h2oframes_works_with_feature_frame():
    """When fr= is passed, iter_h2oframes() yields slices of the feature frame."""
    _reset_cv_warning_state()
    y = _FakeH2OFrame(n=6)
    fr = _FakeH2OFrame(n=6)
    skf = H2OStratifiedKFold(y, n_folds=3, seed=42, fr=fr)
    skf._fold_column = _FakeFoldColumn()
    splits = list(skf.iter_h2oframes())
    assert len(splits) == 3, "Expected 3 fold splits"
    # Each fold issues 2 slices into fr (train + test) = 6 total
    assert len(fr.slice_calls) == 6, "iter_h2oframes must slice the feature frame, not y"


# ---------- fold_assignments accessor ----------------------------------------

def test_fold_assignments_returns_fold_column():
    """`kf.fold_assignments` (a historical public attribute) returns the fold-assignment
    column. It is NOT deprecated — the iteration contract is unchanged, so this remains
    part of the supported API."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    fake = _FakeFoldColumn()
    kf._fold_column = fake
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        result = kf.fold_assignments
    assert caught == [], "fold_assignments must not warn; got %r" % ([str(w.message) for w in caught],)
    assert result is fake, "fold_assignments must return the fold-assignment column"


# ---------- P1-3 -------------------------------------------------------------

def test_iter_h2oframes_does_not_materialize_indices():
    """Server-side iterator must not pull fold assignments to the driver."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    fake_fold_col = _FakeFoldColumn()
    # Inject the fake fold column directly so we can observe whether as_data_frame
    # is called.
    kf._fold_column = fake_fold_col
    splits = list(kf.iter_h2oframes())
    assert len(splits) == 3
    assert fake_fold_col.as_data_frame_calls == [], \
        "iter_h2oframes must NOT call as_data_frame (it would materialize on driver)"
    # The slices into fr should have happened — 2 per fold, train + test.
    assert len(fr.slice_calls) == 6


def test_iter_h2oframes_uses_server_side_masks():
    """Each fold should slice fr with the equivalent of fold_col == k / != k."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    fake_fold_col = _FakeFoldColumn()
    kf._fold_column = fake_fold_col
    list(kf.iter_h2oframes())
    # Each fold issues two comparisons: == fold_index (test) and != fold_index (train)
    eq_calls = [c for c in fake_fold_col._compare_calls if c[0] == "eq"]
    ne_calls = [c for c in fake_fold_col._compare_calls if c[0] == "ne"]
    assert sorted(c[1] for c in eq_calls) == [0, 1, 2]
    assert sorted(c[1] for c in ne_calls) == [0, 1, 2]


def test_iter_h2oframes_does_not_emit_future_warning():
    """The server-side path should not surface the contract-change warning."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    fake_fold_col = _FakeFoldColumn()
    kf._fold_column = fake_fold_col
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        list(kf.iter_h2oframes())
    future = [w for w in caught if issubclass(w.category, FutureWarning)]
    assert future == [], \
        "iter_h2oframes is the server-side path; no contract-change warning expected"


# ---------- split() / get_n_splits() : scikit-learn integer-index contract ---

def test_split_yields_disjoint_covering_int32_index_arrays():
    """The scikit-learn splitter API (added 3.46.0.12): each fold yields
    ``(train_idx, test_idx)`` as 1-D numpy int arrays that are disjoint and together
    cover ``arange(n)`` exactly, with ``n_folds`` pairs total."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    pairs = list(kf.split())
    assert len(pairs) == 3, "expected n_folds=3 (train, test) pairs; got %d" % (len(pairs),)
    seen_test = []
    for train_idx, test_idx in pairs:
        assert isinstance(train_idx, np.ndarray) and isinstance(test_idx, np.ndarray)
        assert np.issubdtype(train_idx.dtype, np.integer)
        assert np.issubdtype(test_idx.dtype, np.integer)
        # train and test partition the full row range with no overlap
        assert set(train_idx) & set(test_idx) == set(), "train/test indices overlap"
        assert sorted(np.concatenate([train_idx, test_idx])) == list(range(6)), \
            "each fold's (train, test) must cover arange(n) exactly"
        seen_test.extend(test_idx.tolist())
    # the test folds across all splits also partition arange(n)
    assert sorted(seen_test) == list(range(6)), "test folds must partition arange(n)"


def test_split_accepts_and_ignores_sklearn_args():
    """scikit-learn calls split(X, y, groups); the extra args must be accepted and
    ignored (fold assignment comes from the frame bound at construction)."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    pairs = list(kf.split(X="ignored", y="ignored", groups="ignored"))
    assert len(pairs) == 3


def test_get_n_splits_returns_n_folds():
    """scikit-learn calls get_n_splits() to size the search without materializing folds."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    assert kf.get_n_splits() == 3
    assert kf.get_n_splits(X="ignored", y="ignored", groups="ignored") == 3


# ---------- _maybe_warn_large_frame ------------------------------------------

def test_maybe_warn_large_frame_below_threshold_is_silent():
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        _maybe_warn_large_frame(_LARGE_FRAME_THRESHOLD)  # boundary: '>' not '>=', so silent
    user = [w for w in caught if issubclass(w.category, UserWarning)]
    assert user == [], "exactly-at-threshold must not warn (guard is strict '>')"


def test_maybe_warn_large_frame_above_threshold_warns_once():
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        _maybe_warn_large_frame(_LARGE_FRAME_THRESHOLD + 1, n_folds=5)
    user = [w for w in caught if issubclass(w.category, UserWarning)]
    assert len(user) == 1, "large frame must emit a single UserWarning; got %d" % (len(user),)
    assert "iter_h2oframes" in str(user[0].message), \
        "warning should point users at the server-side alternative"


# ---------- P1-3: H2OStratifiedKFold fr/y row-count invariant ----------------

def test_stratified_kfold_rejects_fr_y_row_mismatch():
    """fr (feature frame) and y must share row count/order, else folds misalign."""
    y = _FakeH2OFrame(n=6)
    fr = _FakeH2OFrame(n=5)
    try:
        H2OStratifiedKFold(y, n_folds=3, seed=42, fr=fr)
    except ValueError as exc:
        assert "same" in str(exc) and "rows" in str(exc), \
            "error must explain the row-count mismatch; got: %s" % exc
    else:
        raise AssertionError("expected ValueError for mismatched fr/y row counts")


def test_stratified_kfold_accepts_matching_fr_y():
    """Matching row counts must construct without error."""
    y = _FakeH2OFrame(n=6)
    fr = _FakeH2OFrame(n=6)
    H2OStratifiedKFold(y, n_folds=3, seed=42, fr=fr)  # must not raise


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("PASS:", name)
    print("All tests passed.")
