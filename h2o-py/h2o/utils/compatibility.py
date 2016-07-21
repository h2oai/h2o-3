# -*- encoding: utf-8 -*-
"""
Python 2 / 3 compatibility module.

This module gathers common declarations needed to ensure Python 2 / Python 3 compatibility.
It has to be imported from all other files, so that the common header looks like this:

from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

------------------------------------------------------------------------------------------------------------------------
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

3. Iterators
    In Py2 `range`, `filter`, `map` and `zip` produced lists.
    In Py3 same functions return iterators.

    This module backports these functions into Py2, so that they produce iterators both in Py2 and Py3.

4. Dictionaries
    There are multiple inconsistencies between Py2 and Py3 in the behavior of dict.keys(), dict.values(),
    dict.iteritems() / dict.items(), etc.

    This module installs methods `viewkeys`, `viewvalues`, `viewitems` that are preferrable for dict iteration /
    manipulation. These have same semantic as in Py3.

5. Obsolete functions
    Several functions that existed in Py2 were removed in Py3. These are:
        apply, cmp, coerce, execfile, file, long, raw_input, reduce, reload, unicode, xrange, StandardError

    This module replaces all these functions with stubs that produce runtime errors when called. Do not use any of
    these functions if you want your code to be compatible across Py2 / Py3!

6. Miscellaneous
    chr(): In Py2 returns a byte, in Py3 a unicode character.
    input():
    open(): Py3's `open` is equivalent to Py2's `io.open`.
    next(): In Py3 invokes __next__ method of an object, in Py2 doesn't.
    round(): In Py3 returns an integer, in Py2 a float.
    super(): In Py3 a no-argument form is supported, not in Py2.

    All these functions are redefined here to have Py3's behavior on Py2.

------------------------------------------------------------------------------------------------------------------------
:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals
from future.utils import PY2, PY3, with_metaclass

__all__ = ("PY2", "PY3", "with_metaclass", "is_str", "is_int", "is_numeric", "is_listlike", "bytes_iterator",
           "assert_is_type", "assert_is_bool", "assert_is_int", "assert_is_numeric", "assert_is_str",
           "assert_maybe_type", "assert_maybe_int", "assert_maybe_numeric", "assert_maybe_str",
           "range", "filter", "map", "zip", "viewitems", "viewkeys", "viewvalues",
           "apply", "cmp", "coerce", "execfile", "file", "long", "raw_input", "reduce", "reload", "unicode", "xrange",
           "StandardError", "chr", "input", "open", "next", "round", "super", "csv_dict_writer")



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


def is_listlike(s):
    """Return True if s is either a list or a tuple."""
    return isinstance(s, (list, tuple))


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


#-----------------------------------------------------------------------------------------------------------------------
# Iterators
#-----------------------------------------------------------------------------------------------------------------------
if True:
    from future.builtins.iterators import (range, filter, map, zip)
    from future.utils import (viewitems, viewkeys, viewvalues)


#-----------------------------------------------------------------------------------------------------------------------
# Disabled functions
#   -- attempt to use any of these functions will raise an AssertionError now!
#-----------------------------------------------------------------------------------------------------------------------
def _disabled_function(name):
    """Make a function that cannot be called."""
    # noinspection PyUnusedLocal
    def disabled(*args, **kwargs):
        """Disabled function, DO NOT USE."""
        raise NameError("Function %s is not available in Python 3, and was disabled in Python 2 as well." % name)
    return disabled

# noinspection PyShadowingBuiltins
apply = _disabled_function("apply")
# noinspection PyShadowingBuiltins
cmp = _disabled_function("cmp")
# noinspection PyShadowingBuiltins
coerce = _disabled_function("coerce")
# noinspection PyShadowingBuiltins
execfile = _disabled_function("execfile")
# noinspection PyShadowingBuiltins
file = _disabled_function("file")
# noinspection PyShadowingBuiltins
long = _disabled_function("long")
# noinspection PyShadowingBuiltins
raw_input = _disabled_function("raw_input")
# noinspection PyShadowingBuiltins
reduce = _disabled_function("reduce")
# noinspection PyShadowingBuiltins
reload = _disabled_function("reload")
# noinspection PyShadowingBuiltins
unicode = _disabled_function("unicode")
# noinspection PyShadowingBuiltins
xrange = _disabled_function("xrange")
# noinspection PyShadowingBuiltins
StandardError = _disabled_function("StandardError")


#-----------------------------------------------------------------------------------------------------------------------
# Miscellaneous
#-----------------------------------------------------------------------------------------------------------------------
if True:
    from future.builtins.misc import (chr, input, open, next, round, super)


def csv_dict_writer(f, fieldnames, **kwargs):
    """Equivalent of csv.DictWriter, but allows `delimiter` to be a unicode string on Py2."""
    import csv
    if "delimiter" in kwargs:
        kwargs["delimiter"] = str(kwargs["delimiter"])
    return csv.DictWriter(f, fieldnames, **kwargs)
