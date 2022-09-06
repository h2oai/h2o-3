#!/usr/bin/env python
# -*- encoding: utf-8 -*-
from tests import assert_equals
import numpy as np


def should_fail(*args, **kvargs):
    try:
        assert_equals(*args, **kvargs)
    except AssertionError:
        assert True, "Should fail"
        return

    assert False, "Should fail"


def should_pass(*args, **kvargs):
    try:
        assert_equals(*args, **kvargs)
    except AssertionError:
        assert False, "Should pass"


def test_fail_is_failing_properly():
    try:
        should_fail("HELLO", "HELLO")
    except AssertionError as e:
        assert "Should fail" in str(e)
    should_fail("DUNNO", "HELLO")


def test_pass_is_passing_properly():
    try:
        should_pass("ID", "HELLO")
    except AssertionError as e:
        assert "Should pass" in str(e)
    should_pass("HELLO", "HELLO")


def test_assert_equals():
    should_pass("HI", "HI")
    should_pass("10", "10")
    should_pass("10", "10", delta=1e-3)
    should_fail("HI", "HELLO")
    should_fail("HI", "HELLO", delta=1e-3)
    should_fail("HI", 3)
    should_fail("HI", 3, delta=1e-3)
    should_pass(3, 3)
    should_pass(-3, -3)
    should_pass(3.0, 3.0, delta=1e10)
    should_pass(-3.0, -3.0, delta=1e10)
    should_pass(3.2335541321, 3.2339856985, delta=1e-3)
    should_pass(-3.2335541321, -3.2339856985, delta=1e-3)
    should_fail(3.2335541321, 3.2339856985, delta=1e-4)
    should_fail(-3.2335541321, -3.2339856985, delta=1e-4)
    should_fail(3.2335541321, 3.2339856985)
    should_fail(-3.2335541321, -3.2339856985)

    should_pass("nan", "nan")
    should_pass("nan", "nan", delta=1e-3)

    # Traditionally, and per the IEEE floating-point specification, it does not equal itself.
    should_fail(np.nan, np.nan)

    # Allow equality of nans when user specify delta
    should_pass(np.nan, np.nan, delta=1e-4)

    should_fail("nan", np.nan, delta=1e-3)
    should_fail(np.nan, "nan", delta=1e-3)
    should_fail("nan", np.nan)
    should_fail(np.nan, "nan")

    should_pass("inf", "inf")
    should_pass("inf", "inf", delta=1e-3)
    should_pass(np.inf, np.inf)
    should_pass(np.inf, np.inf, delta=1e-4)

    should_fail("inf", np.inf, delta=1e-3)
    should_fail(np.inf, "inf", delta=1e-3)
    should_fail("inf", np.inf)
    should_fail(np.inf, "inf")


# This test doesn't really need a connection to H2O cluster
test_fail_is_failing_properly()
test_pass_is_passing_properly()
test_assert_equals()
