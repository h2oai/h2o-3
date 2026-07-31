#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
Regression tests for free-function calls inside H2OFrame lambdas (astfun.py).

Pre-3.46.0.12, ``frame.apply(lambda x: some_global_fn(x, 2))`` silently discarded
the call arguments and produced an ExprNode with zero children — the call appeared
to work but evaluated to the bare function name. ``_to_rapids_expr`` now raises a
clear ValueError for free-function calls that carry arguments, so the user switches
to a method-style lambda. This pins both the raise and the still-working method path.

These tests do not require an H2O server connection.
"""
import h2o
from h2o.astfun import lambda_to_expr


def _free_helper(a, b):
    return a + b


def test_free_function_call_with_args_raises():
    """A free (non-method) call carrying args must raise, not silently drop them."""
    try:
        lambda_to_expr(lambda x: _free_helper(x, 2))
    except ValueError as exc:
        assert "Free-function" in str(exc), \
            "expected the free-function diagnostic; got: %s" % exc
    else:
        raise AssertionError(
            "lambda_to_expr must raise ValueError for a free-function call with args, "
            "instead of silently producing a zero-child ExprNode"
        )


def test_builtin_free_function_call_with_args_raises():
    """Same break for a builtin free function (loaded via LOAD_GLOBAL)."""
    try:
        lambda_to_expr(lambda x: round(x, 2))
    except ValueError as exc:
        assert "Free-function" in str(exc), \
            "expected the free-function diagnostic; got: %s" % exc
    else:
        raise AssertionError("lambda_to_expr must raise for round(x, 2)")


def test_method_call_still_translates():
    """The method path (LOAD_ATTR/LOAD_METHOD) must NOT hit the free-function raise."""
    nodes = lambda_to_expr(lambda x: x.scale(center=True, scale=False))
    expr = nodes[3]  # strip the lambda-frame preamble
    assert expr._op == "scale", "method lambda must translate to a Rapids op; got %r" % (expr._op,)


if __name__ == "__main__":
    test_free_function_call_with_args_raises()
    print("PASS: test_free_function_call_with_args_raises")
    test_builtin_free_function_call_with_args_raises()
    print("PASS: test_builtin_free_function_call_with_args_raises")
    test_method_call_still_translates()
    print("PASS: test_method_call_still_translates")
    print("All tests passed.")
