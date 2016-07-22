# -*- encoding: utf-8 -*-
"""
Utilities for checking types of variables.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

from h2o.utils.compatibility import *  # NOQA
from h2o.utils.compatibility import _native_unicode, _native_long

__all__ = ("is_str", "is_int", "is_numeric", "is_listlike", "assert_is_type", "assert_is_bool", "assert_is_int",
           "assert_is_numeric", "assert_is_str", "assert_maybe_type", "assert_maybe_int", "assert_maybe_numeric",
           "assert_maybe_str")


if PY2:
    _str_type = (str, _native_unicode)
    _int_type = (int, _native_long)
    _num_type = (int, _native_long, float)
else:
    _str_type = str
    _int_type = int
    _num_type = (int, float)




def is_str(s):
    """Test whether the provided argument is a string."""
    return isinstance(s, _str_type)


def is_int(i):
    """Test whether the provided argument is an integer."""
    return isinstance(i, _int_type)


def is_numeric(x):
    """Test whether the provided argument is either an integer or a float."""
    return isinstance(x, _num_type)


def is_listlike(s):
    """Return True if s is either a list or a tuple."""
    return isinstance(s, (list, tuple))



def assert_is_type(s, name, stype, typename=None):
    """
    Assert that the argument has the specified type.

    This function is used to check that the type of the argument is correct, or otherwise raise an error. Usage:
        assert_is_str(url, "url")
    :param s: variable to check
    :param name: name of the variable to report in the error message (optional)
    :param stype: expected type
    :param typename: name of the type (if not given, will be extracted from `stype`)
    :raise ValueError if the argument is not of the desired type.
    """
    if not isinstance(s, stype):
        nn = "`%s`" % name if name else "Argument"
        tn = typename or type(stype).__name__
        raise ValueError("%s should have been a %s, got %s" % (nn, tn, type(s)))

def assert_maybe_type(s, name, stype, typename=None):
    """Assert that the argument is either of the specified type or None."""
    if not (s is None or isinstance(s, stype)):
        nn = "`%s`" % name if name else "Argument"
        tn = typename or type(stype).__name__
        raise ValueError("%s should have been a %s, got %s" % (nn, tn, type(s)))


def assert_is_str(s, name=None):
    """Assert that the argument is a string."""
    assert_is_type(s, name, _str_type, "string")

def assert_maybe_str(s, name=None):
    """Assert that the argument is a string or None."""
    assert_maybe_type(s, name, _str_type, "string")

def assert_is_int(x, name=None):
    """Assert that the argument is integer."""
    assert_is_type(x, name, _int_type, "integer")

def assert_maybe_int(x, name=None):
    """Assert that the argument is integer or None."""
    assert_maybe_type(x, name, _int_type, "integer")

def assert_is_bool(b, name=None):
    """Assert that the argument is boolean."""
    assert_is_type(b, name, bool, "boolean")

def assert_is_numeric(x, name=None):
    """Assert that the argument is numeric (integer or float)."""
    assert_is_type(x, name, _num_type, "numeric")

def assert_maybe_numeric(x, name=None):
    """Assert that the argument is either numeric or None."""
    assert_maybe_type(x, name, _num_type, "numeric")
