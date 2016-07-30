#!/usr/bin/env python
# -*- encoding: utf-8 -*-
"""Pyunit for h2o.utils.typechecks."""
from __future__ import absolute_import, division, print_function
from h2o.exceptions import H2OTypeError, H2OValueError
from h2o.utils.typechecks import (U, I, numeric, assert_is_type, assert_matches, assert_satisfies)

def test_asserts():
    """Test type-checking functionality."""
    def assert_error(*args, **kwargs):
        """Check that assert_is_type() with given arguments throws an error."""
        try:
            assert_is_type(*args, **kwargs)
            raise RuntimeError("Failed to throw an exception")
        except H2OTypeError as e:
            # Check whether the message can stringify properly
            message = str(e)
            assert len(message) < 1000
            return

    class A(object): pass

    class B(A): pass

    class C(A): pass

    class D(B, C): pass


    assert_is_type(3, int)
    assert_is_type(2**100, int)
    assert_is_type("3", str)
    assert_is_type(u"3", str)
    assert_is_type("foo", u"foo")
    assert_is_type(u"foo", "foo")
    assert_is_type("I", *list("ABCDEFGHIJKL"))
    assert_is_type(False, bool)
    assert_is_type(43, str, bool, int)
    assert_is_type(4 / 3, int, float)
    assert_is_type(None, None)
    assert_is_type(None, A, str, None)
    assert_is_type([], [float])
    assert_is_type([1, 4, 5], [int])
    assert_is_type([1.0, 2, 5], [int, float])
    assert_is_type([[2.0, 3.1, 0], [2, 4.4, 1.1], [-1, 0]], [[int, float]])
    assert_is_type([1, None, 2], [int, float, None])
    assert_is_type({1, 5, 1, 1, 3}, {int})
    assert_is_type({1, "hello", 3}, {int, str})
    assert_is_type({"foo": 1, "bar": 2}, {str: int})
    assert_is_type({"foo": 3, "bar": [5], "baz": None}, {str: U(int, None, [int])})
    assert_is_type({"foo": 1, "bar": 2}, {"foo": int, "bar": U(int, float, None), "baz": bool})
    assert_is_type((1, 3), (int, int))
    assert_is_type(("a", "b", "c"), (int, int, int), (str, str, str))
    assert_is_type([1, [2], [{3}]], [int, [int], [{3}]])
    assert_is_type(A(), None, A)
    assert_is_type(B(), None, A)
    assert_is_type(C(), A, B)
    assert_is_type(D(), I(A, B, C))
    assert_is_type(A, type)
    for a in range(-2, 5):
        assert_is_type(a, -2, -1, 0, 1, 2, 3, 4)
    assert_is_type(1, numeric)
    assert_is_type(2.2, numeric)
    assert_is_type(1, I(numeric, object))

    assert_error(3, str)
    assert_error("Z", *list("ABCDEFGHIJKL"))
    assert_error(u"Z", "a", "...", "z")
    assert_error("X", u"x")
    assert_error(0, bool)
    assert_error(0, float, str, bool, None)
    assert_error([1, 5], [float])
    assert_error((1, 3), (int, str), (str, int), (float, float))
    assert_error(A(), None, B)
    assert_error(A, A)
    assert_error({"foo": 1, "bar": "2"}, {"foo": int, "bar": U(int, float, None)})
    assert_error(3, 0, 2, 4)
    assert_error(None, numeric)
    assert_error("sss", numeric)
    assert_error(B(), I(A, B, C))
    assert_error(2, I(int, str))

    url_regex = r"^(https?)://((?:[\w-]+\.)*[\w-]+):(\d+)/?$"
    assert_matches("Hello, world!", r"^(\w+), (\w*)!$")
    assert_matches("http://127.0.0.1:3233/", url_regex)
    m = assert_matches("https://localhost:54321", url_regex)
    assert m.group(1) == "https"
    assert m.group(2) == "localhost"
    assert m.group(3) == "54321"

    x = 5
    assert_satisfies(x, x < 1000)
    assert_satisfies(x, x ** x > 1000)
    assert_satisfies(url_regex, url_regex.lower() == url_regex)
    try:
        assert_satisfies(url_regex, url_regex.upper() == url_regex)
    except H2OValueError as e:
        assert "url_regex.upper() == url_regex" in str(e), "Error message is bad: " + str(e)


# This test doesn't really need a connection to H2O cluster.
test_asserts()
