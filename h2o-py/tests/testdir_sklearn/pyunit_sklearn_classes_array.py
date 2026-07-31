#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
Regression tests for ``h2o.sklearn.wrapper._classes_array``.

Earlier versions silently coerced any all-digit string domain to ``np.int64``,
which corrupted class identities for leading-zero labels (``['01','02']``
→ ``[1, 2]``). They also crashed on tokens like ``'--5'`` that pass the
``lstrip('-').isdigit()`` heuristic but raise ``ValueError`` in ``np.asarray``.

These tests run without an H2O server (the helpers are pure Python).
"""
import numpy as np

from h2o.sklearn.wrapper import _classes_array, _is_canonical_int


# ---------- _is_canonical_int -------------------------------------------------

def test_is_canonical_int_accepts_canonical_decimals():
    for s in ("0", "1", "10", "-1", "-10", "123456789"):
        assert _is_canonical_int(s) is True, "%r should be canonical" % (s,)


def test_is_canonical_int_rejects_leading_zeros():
    for s in ("01", "00", "001", "-01"):
        assert _is_canonical_int(s) is False, \
            "%r has a leading zero — must not be considered canonical" % (s,)


def test_is_canonical_int_rejects_negative_zero():
    # int("-0") == 0, str(0) == "0", which != "-0", so "-0" is not canonical.
    assert _is_canonical_int("-0") is False


def test_is_canonical_int_rejects_non_canonical_tokens():
    for s in ("--5", "+1", "1_000", " 1", "1 ", "1.0", "0x1f", "²", "", None):
        assert _is_canonical_int(s) is False, "%r should be rejected" % (s,)


# ---------- _classes_array ----------------------------------------------------

def test_classes_array_canonical_binary():
    arr = _classes_array(["0", "1"])
    assert isinstance(arr, np.ndarray)
    assert arr.dtype == np.int64
    assert list(arr) == [0, 1]


def test_classes_array_canonical_negatives():
    arr = _classes_array(["-2", "-1", "0", "1", "2"])
    assert arr.dtype == np.int64
    assert list(arr) == [-2, -1, 0, 1, 2]


def test_classes_array_leading_zero_labels_preserved_as_strings():
    # The headline bug: ['01','02'] used to silently become [1, 2].
    arr = _classes_array(["01", "02"])
    assert arr.dtype.kind == "U", \
        "leading-zero labels must stay as strings; got dtype %r" % (arr.dtype,)
    assert list(arr) == ["01", "02"]


def test_classes_array_string_labels_preserved():
    arr = _classes_array(["cat", "dog"])
    assert arr.dtype.kind == "U"
    assert list(arr) == ["cat", "dog"]


def test_classes_array_pathological_double_minus_does_not_crash():
    # Used to escape the (AttributeError, TypeError) guard and propagate ValueError.
    arr = _classes_array(["--5", "x"])
    assert arr.dtype.kind == "U"
    assert list(arr) == ["--5", "x"]


def test_classes_array_float_strings_preserved_as_strings():
    # Decision: do not auto-coerce float-looking strings — sklearn LabelEncoder
    # does not either, and class labels with a fractional part are unusual.
    arr = _classes_array(["1.5", "2.5"])
    assert arr.dtype.kind == "U"


def test_classes_array_mixed_int_and_non_int_falls_back_to_strings():
    arr = _classes_array(["1", "x"])
    assert arr.dtype.kind == "U"
    assert list(arr) == ["1", "x"]


def test_classes_array_int_overflow_falls_back_to_strings():
    # INT64_MAX+1 (2**63) is a *canonical* Python int (it round-trips through
    # ``int()``), so _is_canonical_int accepts it — but it overflows np.int64.
    # _classes_array must fall back to a string array rather than crash or
    # silently wrap the value.
    big = "9223372036854775808"  # 2**63
    assert _is_canonical_int(big) is True
    arr = _classes_array([big, "1"])
    assert arr.dtype.kind == "U", \
        "out-of-int64-range labels must stay strings; got dtype %r" % (arr.dtype,)
    assert list(arr) == [big, "1"]


def test_classes_array_int64_max_stays_int():
    # The exact INT64_MAX boundary still fits and must remain integer-typed.
    arr = _classes_array(["0", "9223372036854775807"])  # 2**63 - 1
    assert arr.dtype == np.int64
    assert list(arr) == [0, 9223372036854775807]


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("PASS:", name)
    print("All tests passed.")
