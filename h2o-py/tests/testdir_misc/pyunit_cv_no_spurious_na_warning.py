#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
Regression tests for h2o.cross_validation helpers:

  * ``_materialize_fold_column`` must not emit the spurious
    ``_warn_as_data_frame_na_default`` FutureWarning on every CV iteration
    (P0-3).
  * ``H2OKFold`` must announce its contract change as a ``FutureWarning``
    (Python's standard "behavior is changing" class) — not
    ``H2ODeprecationWarning``, since the contract changed without a real
    deprecation cycle (P1-1).
  * ``H2OPartitionIterator.iter_h2oframes`` must keep the split server-side
    (P1-3) — no driver-side materialization of fold indices.

Tests run without an H2O server connection by duck-typing the H2OFrame and
fold-column objects.
"""
import warnings

import numpy as np
import pandas as pd

from h2o.cross_validation import (
    _materialize_fold_column,
    H2OKFold,
    H2OStratifiedKFold,
)


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


class _FakeMaskedComparison(object):
    """Returned by ``fold_col[colname]`` — records ``== fold`` and ``!= fold``."""

    def __init__(self, parent, col):
        self.parent = parent
        self.col = col

    def __eq__(self, other):
        self.parent._compare_calls.append(("eq", other))
        return ("mask_eq", other)

    def __ne__(self, other):
        self.parent._compare_calls.append(("ne", other))
        return ("mask_ne", other)


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


# ---------- P1-1 -------------------------------------------------------------

def test_h2okfold_emits_future_warning_not_deprecation():
    """The contract-change warning must be a FutureWarning, not a DeprecationWarning."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        list(iter(kf))
    future = [w for w in caught if issubclass(w.category, FutureWarning)]
    deprec = [w for w in caught if issubclass(w.category, DeprecationWarning)]
    assert future, "Expected a FutureWarning announcing the contract change"
    assert not deprec, \
        "Contract change is a hard break — must not surface as DeprecationWarning: %r" \
        % ([str(w.message) for w in deprec],)


def test_h2ostratifiedkfold_emits_future_warning():
    fr = _FakeH2OFrame(n=6)
    kf = H2OStratifiedKFold(fr, n_folds=3, seed=42)
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        list(iter(kf))
    future = [w for w in caught if issubclass(w.category, FutureWarning)]
    assert future, "Expected a FutureWarning"


def test_contract_change_warning_is_one_shot_per_iterator():
    """Repeated iter() on the same iterator must not re-emit the FutureWarning."""
    fr = _FakeH2OFrame(n=6)
    kf = H2OKFold(fr, n_folds=3, seed=42)
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        list(iter(kf))
        list(iter(kf))
        list(iter(kf))
    future = [w for w in caught if issubclass(w.category, FutureWarning)]
    assert len(future) == 1, \
        "Expected exactly one FutureWarning across repeated iter() calls; got %d" \
        % (len(future),)


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


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("PASS:", name)
    print("All tests passed.")
