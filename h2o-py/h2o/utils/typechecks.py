# -*- encoding: utf-8 -*-
"""
Utilities for checking types of variables.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import linecache
import re
import sys

from h2o.utils.compatibility import *  # NOQA
from h2o.exceptions import H2OTypeError, H2OValueError

__all__ = ("is_str", "is_int", "is_numeric", "is_listlike", "assert_is_type", "assert_is_bool", "assert_is_int",
           "assert_is_numeric", "assert_is_str", "assert_maybe_type", "assert_maybe_int", "assert_maybe_numeric",
           "assert_maybe_str")


if PY2:
    # noinspection PyProtectedMember
    from h2o.utils.compatibility import _native_unicode, _native_long
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



def assert_is_type(s, stype, typename=None, skip_frames=1):
    """
    Assert that the argument has the specified type.

    This function is used to check that the type of the argument is correct, or otherwise raise an error.
    For example::

        assert_is_type(fr, H2OFrame)

    :param s: variable to check
    :param stype: expected type
    :param typename: name of the type (if not given, will be extracted from `stype`)
    :param skip_frames: how many local frames to skip when printing out the error.

    :raises H2OTypeError: if the argument is not of the desired type.
    """
    if not isinstance(s, stype):
        nn = _get_variable_name()
        tn = typename or _get_type_name(stype)
        if tn[0] in "aioe" or tn.startswith("H2"):
            tn = "an " + tn
        else:
            tn = "a " + tn
        sn = _get_type_name(type(s))
        raise H2OTypeError("`%s` should be %s, got %r (type <%s>)" % (nn, tn, s, sn),
                           skip_frames=skip_frames)


def assert_maybe_type(s, stype, typename=None, skip_frames=1):
    """Assert that the argument is either of the specified type or None."""
    if not (s is None or isinstance(s, stype)):
        nn = _get_variable_name()
        tn = typename or _get_type_name(stype)
        sn = _get_type_name(type(s))
        raise H2OTypeError("`%s` should be a %s, got %r (type <%s>)" % (nn, tn, s, sn),
                           skip_frames=skip_frames)

def assert_is_none(s, reason=None):
    """Assert that the argument is None."""
    if s is not None:
        raise H2OValueError("`%s` should be None %s" % (_get_variable_name(), reason or ""))

def assert_is_str(s):
    """Assert that the argument is a string."""
    assert_is_type(s, _str_type, "string", skip_frames=2)

def assert_maybe_str(s):
    """Assert that the argument is a string or None."""
    assert_maybe_type(s, _str_type, "string", skip_frames=2)

def assert_is_int(x):
    """Assert that the argument is integer."""
    assert_is_type(x, _int_type, "integer", skip_frames=2)

def assert_maybe_int(x):
    """Assert that the argument is integer or None."""
    assert_maybe_type(x, _int_type, "integer", skip_frames=2)

def assert_is_bool(b):
    """Assert that the argument is boolean."""
    assert_is_type(b, bool, "boolean", skip_frames=2)

def assert_is_numeric(x):
    """Assert that the argument is numeric (integer or float)."""
    assert_is_type(x, _num_type, "numeric", skip_frames=2)

def assert_maybe_numeric(x):
    """Assert that the argument is either numeric or None."""
    assert_maybe_type(x, _num_type, "numeric", skip_frames=2)


def _get_variable_name():
    """
    Magic variable name retrieval.

    This function is designed as a helper for assert_*() functions. Typically such assertion is used like this::

        assert_is_int(num_threads)

    If the variable `num_threads` turns out to be non-integer, we would like to raise an exception such as

        H2OTypeError("`num_threads` is expected to be integer, but got <str>")

    and in order to compose an error message like that, we need to know that the variables that was passed to
    assert_is_int() carries a name "num_threads". Naturally, the variable itself knows nothing about that.

    This is where this function comes in: we walk up the stack trace until the first frame outside of this
    file, find the original line that called the assert_is_int() function, and extract the variable name from
    that line. This is slightly fragile, in particular we assume that only one assert_* statement can be per line,
    or that this statement does not spill over multiple lines, or that the argument is not a complicated
    expression like `assert_is_int(foo(x))` or `assert_is_str(x[1,2])`. I do not foresee such complexities in the
    code, but if they arise this function can be amended to parse those cases properly.
    """
    try:
        raise RuntimeError("Catch me!")
    except RuntimeError:
        # Walk up the stacktrace until we are outside of this file
        tb = sys.exc_info()[2]
        assert tb.tb_frame.f_code.co_name == "_get_variable_name"
        this_filename = tb.tb_frame.f_code.co_filename
        fr = tb.tb_frame
        while fr is not None and fr.f_code.co_filename == this_filename:
            fr = fr.f_back

        # Retrieve the line of code where assert* statement is expected be
        linecache.checkcache(fr.f_code.co_filename)
        line = linecache.getline(fr.f_code.co_filename, fr.f_lineno)

        # Find the variable of interest and return it
        variables = re.findall(r"assert_(?:is|maybe)_\w+\(([^,)]*)", line)
        if len(variables) == 0: return "<arg>"
        if len(variables) == 1: return variables[0]
        raise RuntimeError("More than one assert_*() statement on the line!")


def _get_type_name(stype):
    """
    Return name of the provided type.

    Examples:
    >>> _get_type_name(int) == "int"
    >>> _get_type_name(tuple) == "tuple"
    >>> _get_type_name(Exception) == "Exception"
    >>> _get_type_name((int, long, float)) == "int | long | float"
    """
    if type(stype) is type:
        return stype.__name__
    elif type(stype) is tuple and all(type(t) is type for t in stype):
        return " | ".join(t.__name__ for t in stype)
    else:
        raise RuntimeError("Unexpected `stype`: %r" % stype)
