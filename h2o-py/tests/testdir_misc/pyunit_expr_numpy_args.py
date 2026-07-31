#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
Regression tests for ``h2o.expr.ExprNode`` numpy-scalar handling.

numpy 2.x changed scalar ``repr`` to embed a type wrapper::

    repr(np.float64(0.1)) == "np.float64(0.1)"   # was "0.1"
    repr(np.str_("foo"))  == "np.str_('foo')"    # was "'foo'"

Both forms are unparseable by H2O's Rapids interpreter, which expects bare
literals. ``ExprNode._to_python_scalar`` / ``_arg_to_expr`` unwrap numpy scalars
(including inside nested lists and slice start/stop/step) back to plain Python
before any ``repr()`` is emitted.

These paths only activate under numpy >= 2 (i.e. Python 3.12+ in this project's
matrix), and the existing ``pyunit_frame_numpy_indexing`` test only covers a bare
``fr[np.int64(1)]`` column index — the slice and nested-list branches were
otherwise unguarded. The assertions here hold on both numpy 1.x and 2.x (on 1.x
the repr already lacked the wrapper), so the test runs on every Python version
with numpy installed. No H2O server connection required.
"""
import math
import sys

try:
    import numpy as np
except ImportError:
    print("SKIP: numpy not installed")
    sys.exit(0)

from h2o.expr import ExprNode

_arg = ExprNode._arg_to_expr
_scalar = ExprNode._to_python_scalar


# ---------- _to_python_scalar : unwrap to plain Python types ----------------

def test_to_python_scalar_unwraps_numpy_float():
    out = _scalar(np.float64(2.5))
    assert out == 2.5 and type(out) is float, "got %r (%s)" % (out, type(out))


def test_to_python_scalar_unwraps_numpy_int():
    out = _scalar(np.int64(7))
    assert out == 7 and type(out) is int, "got %r (%s)" % (out, type(out))


def test_to_python_scalar_unwraps_numpy_str_subclass():
    out = _scalar(np.str_("foo"))
    assert out == "foo" and type(out) is str, "got %r (%s)" % (out, type(out))


def test_to_python_scalar_leaves_plain_python_untouched():
    for v in (1, 2.5, "x", True, None):
        assert _scalar(v) is v or _scalar(v) == v


# ---------- _arg_to_expr : no leaked numpy repr in the Rapids string --------
# The single invariant that matters across numpy versions: the emitted Rapids
# fragment must never contain the "np." type wrapper, which the JVM rejects.

def _assert_no_numpy_leak(label, value, expected=None):
    out = _arg(value)
    assert "np." not in out, \
        "%s leaked a numpy repr into Rapids: %r" % (label, out)
    if expected is not None:
        assert out == expected, "%s: got %r, expected %r" % (label, out, expected)


def test_arg_to_expr_numpy_scalar_float():
    _assert_no_numpy_leak("np.float64 scalar", np.float64(2.5), "2.5")


def test_arg_to_expr_numpy_scalar_int():
    _assert_no_numpy_leak("np.int64 scalar", np.int64(7), "7")


def test_arg_to_expr_numpy_scalar_str():
    _assert_no_numpy_leak("np.str_ scalar", np.str_("hi"), "'hi'")


def test_arg_to_expr_list_of_numpy_ints():
    _assert_no_numpy_leak("list of np ints", [np.int64(1), np.int64(2)], "[1 2]")


def test_arg_to_expr_nested_list_of_numpy_floats():
    # The nested-list recursion: a top-level unwrap only handles
    # ``[np.float64(1)]``; ``[[np.float64(1.5)]]`` would otherwise slip through
    # because repr() of the inner list re-emits ``np.float64(1.5)``.
    _assert_no_numpy_leak("nested np floats",
                          [[np.float64(1.5)], [np.float64(2.5)]],
                          "[[1.5] [2.5]]")


def test_arg_to_expr_numpy_slice_unwraps_start_stop_step():
    # slice(np.int64(0), np.int64(5), np.int64(2)) (e.g. fr[np.int64(0):np.int64(5):np.int64(2)]).
    # start/stop/step must be unwrapped before the arithmetic that builds the
    # Rapids "[start:len:step]" fragment, or "np.int64(5)" leaks in.
    out = _arg(slice(np.int64(0), np.int64(5), np.int64(2)))
    assert "np." not in out, "numpy slice leaked a numpy repr: %r" % (out,)
    assert out == "[0:3:2]", "got %r" % (out,)  # start:(stop-start):step


def test_arg_to_expr_numpy_slice_no_step():
    out = _arg(slice(np.int64(2), np.int64(6)))
    assert "np." not in out and out == "[2:4]", "got %r" % (out,)


if __name__ == "__main__":
    failed = []
    for name, fn in list(globals().items()):
        if not name.startswith("test_") or not callable(fn):
            continue
        try:
            fn()
            print("PASS:", name)
        except AssertionError as exc:
            failed.append((name, exc))
            print("FAIL:", name, "—", exc)
    if failed:
        sys.exit(1)
    print("All tests passed.")
