#!/usr/bin/env python3
# -*- encoding: utf-8 -*-
"""
Unit tests for the should_be_skipped function in astfun.py.

Tests confirm the tuple-precedence bug fix (BYTE-01 / D-03):
  - Before fix: `return instr in "COPY_FREE_VARS", "RESUME", "PUSH_NULL"` produces a
    3-tuple (bool, str, str) that is always truthy.
  - After fix: `return instr in {"COPY_FREE_VARS", "RESUME", "PUSH_NULL"}` is a correct
    set membership test.

These tests run without requiring an H2O server connection.
"""
import ast
import os
import h2o
from h2o.astfun import should_be_skipped


# Verify the fix is in place via AST inspection
def test_should_be_skipped_uses_set_literal():
    """The function body must use a set literal, not a tuple expression."""
    src = open(os.path.join(os.path.dirname(h2o.__file__), 'astfun.py')).read()
    tree = ast.parse(src)
    for node in ast.walk(tree):
        if isinstance(node, ast.FunctionDef) and node.name == 'should_be_skipped':
            ret = node.body[0]
            assert isinstance(ret, ast.Return), "Expected return statement"
            cmp = ret.value
            assert isinstance(cmp, ast.Compare), "Expected Compare node, got %s" % type(cmp)
            assert any(isinstance(c, ast.Set) for c in cmp.comparators), \
                "Expected Set comparator -- function uses a tuple, not a set"
            return
    raise AssertionError("should_be_skipped function not found in astfun.py")


# Behavioural tests exercise the actual h2o.astfun.should_be_skipped symbol so a
# future regression to the tuple form would be caught by THESE tests, not only by
# the AST-shape test above.
def test_should_be_skipped_true_for_skip_opcodes():
    assert should_be_skipped("COPY_FREE_VARS") is True
    assert should_be_skipped("RESUME") is True
    assert should_be_skipped("PUSH_NULL") is True


def test_should_be_skipped_false_for_non_skip_opcodes():
    assert should_be_skipped("LOAD_CONST") is False
    assert should_be_skipped("CALL") is False
    assert should_be_skipped("RETURN_VALUE") is False
    assert should_be_skipped("") is False


if __name__ == "__main__":
    test_should_be_skipped_uses_set_literal()
    print("PASS: test_should_be_skipped_uses_set_literal")
    test_should_be_skipped_true_for_skip_opcodes()
    print("PASS: test_should_be_skipped_true_for_skip_opcodes")
    test_should_be_skipped_false_for_non_skip_opcodes()
    print("PASS: test_should_be_skipped_false_for_non_skip_opcodes")
    print("All tests passed.")
