# -*- encoding: utf-8 -*-
"""
Python 2 / 3 compatibility module.

This module gathers common declarations needed to ensure Python 2 / Python 3 compatibility.
It has to be imported from all other files, so that the common header looks like this:

from __future__ import absolute_import, division, print_function, unicode_literals
from .compatibility import *  # NOQA

------------------------------------------------------------------------

1. Strings
    In Py2 `str` is a byte string (`bytes is str == True`), and `unicode` is a unicode string.
    In Py3 `str` is a unicode string, `bytes` is a byte string, and symbol `unicode` is not defined.
    Iterating over a `bytes` string in Py2 produces characters, in Py3 character codes.

    For consistent results, use
        is_str(s)  to test whether an argument is a string
        bytes_iterator(s)  to iterate over byte-codes of strins s (which could be bytes or unicode)

2. Integers
    In Py2 there are two integer types: `int` and `long`. The latter has suffix "L" when stringified with repr().
    In Py3 `int` is a single integer type, `long` doesn't exist.

    For consistent results, use
        is_int(x)  to test whether an argument is an integer
        str(x)  to convert x to string (don't use repr()!)


  :copyright: (c) 2016 H2O.ai
  :license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals
# noinspection PyUnresolvedReferences
from future.utils import PY2, PY3, with_metaclass  # NOQA


#-----------------------------------------------------------------------------------------------------------------------
# Type utilities
#-----------------------------------------------------------------------------------------------------------------------

if PY2:
    _str_type = (str, unicode)
    _int_type = (int, long)
    _num_type = (int, long, float)
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


def bytes_iterator(s):
    """Given a string, return an iterator over this string's bytes (as ints)."""
    if s is None: return
    if PY2 or PY3 and isinstance(s, str):
        for ch in s:
            yield ord(ch)
    elif PY3 and isinstance(s, bytes):
        for ch in s:
            yield ch
    else:
        raise TypeError("String argument expected, got %s" % type(s))


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




#
# Iterators
#
if PY2:
    # noinspection PyUnresolvedReferences
    from future.builtins.iterators import (range, filter, map, zip)  # NOQA
if PY2 or PY3:
    # noinspection PyUnresolvedReferences
    from future.utils import (viewitems, viewkeys, viewvalues)  # NOQA

#
# Disabled functions
#   -- attempt to use any of these functions will raise an AssertionError now!
#
if PY2:
    # noinspection PyUnresolvedReferences
    from future.builtins.disabled import (apply, cmp, coerce, execfile, file, long, raw_input,  # NOQA
                                          reduce, reload, unicode, xrange, StandardError)       # NOQA

#
# Miscellaneous
#
if PY2:
    # noinspection PyUnresolvedReferences
    from future.builtins.misc import (ascii, chr, hex, input, next, oct, open, pow, round, super)  # NOQA


def csv_dict_writer(f, fieldnames, **kwargs):
    import csv
    if "delimiter" in kwargs:
        delim = kwargs.pop("delimiter")
        if PY2: delim = str(delim).encode("utf-8")
        if PY3: delim = str(delim)
        kwargs["delimiter"] = delim
    return csv.DictWriter(f, fieldnames, **kwargs)
