#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
Regression tests for keyword-argument ordering in astfun.py disassembly.

Bug: ``_call_bc`` (KW_NAMES path, Py 3.11/3.12), ``_call_func_kw_bc``
(CALL_FUNCTION_KW, Py 3.6-3.10) and ``_call_kw_bc`` (CALL_KW, Py 3.13+) all
iterated the keyword-name tuple in forward order while popping values from
top-of-stack. CPython pushes keyword *values* in left-to-right kwnames-tuple
order, so the value for the LAST kwname sits on top of stack — walking the
tuple forward bound it to the FIRST kwname, swapping all kwarg values.

The existing test in ``pyunit_apply.py`` masked the bug because it used
``scale(center=False, scale=False)`` — identical values for both kwargs.

These tests do not require an H2O server connection.
"""
import h2o
from h2o.astfun import lambda_to_expr


def _body_of(lam):
    """Strip the lambda preamble [ASTId('{'), ASTId(<arg>), ASTId('.'), <body>, ASTId('}')]."""
    nodes = lambda_to_expr(lam)
    return nodes[3]


def test_two_distinct_kwargs():
    """scale(center=True, scale=False) must NOT swap the values."""
    expr = _body_of(lambda x: x.scale(center=True, scale=False))
    assert expr._op == "scale"
    # H2OFrame.scale signature: scale(center=True, scale=True) → method args are
    # [center_value, scale_value]. The disassembled expression is
    # (scale self center_value scale_value).
    assert expr._children[1] is True, \
        "center should be True; got %r — kwargs are swapped" % (expr._children[1],)
    assert expr._children[2] is False, \
        "scale should be False; got %r — kwargs are swapped" % (expr._children[2],)


def test_two_distinct_kwargs_reversed():
    """Symmetric check with values swapped."""
    expr = _body_of(lambda x: x.scale(center=False, scale=True))
    assert expr._op == "scale"
    assert expr._children[1] is False
    assert expr._children[2] is True


def test_three_distinct_kwargs():
    """merge(other=10, all_x=True, by_x='a') exercises 3-kwarg ordering."""
    expr = _body_of(lambda x: x.merge(other=10, all_x=True, by_x="a"))
    assert expr._op == "merge"
    # merge signature: merge(other, all_x=False, all_y=False, by_x=None, by_y=None, method="auto")
    # Resulting positional args after the self ref:
    #   [other, all_x, all_y, by_x, by_y, method]
    children = expr._children
    assert children[1] == 10, "other should be 10; got %r" % (children[1],)
    assert children[2] is True, "all_x should be True; got %r" % (children[2],)
    assert children[3] is False, "all_y should default to False; got %r" % (children[3],)
    assert children[4] == "a", "by_x should be 'a'; got %r" % (children[4],)


def test_kwargs_with_positional_mix():
    """Mixing positional + keyword args must keep both groups in order."""
    expr = _body_of(lambda x: x.scale(True, scale=False))
    # Positional True goes to `center`; keyword scale=False
    assert expr._op == "scale"
    assert expr._children[1] is True
    assert expr._children[2] is False


if __name__ == "__main__":
    test_two_distinct_kwargs()
    print("PASS: test_two_distinct_kwargs")
    test_two_distinct_kwargs_reversed()
    print("PASS: test_two_distinct_kwargs_reversed")
    test_three_distinct_kwargs()
    print("PASS: test_three_distinct_kwargs")
    test_kwargs_with_positional_mix()
    print("PASS: test_kwargs_with_positional_mix")
    print("All tests passed.")
