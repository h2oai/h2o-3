# -*- encoding: utf-8 -*-
"""
Python 2 / 3 compatibility module.

This module gathers common declarations needed to ensure Python 2 / Python 3 compatibility.
It has to be imported from all other files, so that the common header looks like this:

from __future__ import absolute_import, division, print_function, unicode_literals
from .compatibility import *  # NOQA

  :copyright: (c) 2016 H2O.ai
  :license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals
# noinspection PyUnresolvedReferences
from future.utils import PY2, PY3, with_metaclass  # NOQA

# Store original type declarations, in case we need them later
native_bytes = bytes
native_dict = dict
native_int = int
native_list = list
native_object = object
native_str = str
if PY2:
    # "unicode" and "long" symbols don't exist in PY3, so
    native_unicode = unicode
    native_long = long
    _str_type = (str, unicode)
    _int_type = (int, long)
    _num_type = (int, long, float)
else:
    _str_type = str
    _int_type = int
    _num_type = (int, float)



#-----------------------------------------------------------------------------------------------------------------------
# Type utilities
#-----------------------------------------------------------------------------------------------------------------------

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
        for ch in s.encode("utf-8"):
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


def translate_args(fun):
    """
    This decorator ensures that arguments supplied to a function are Python-3 compatible.
    The problem that it tries to solve is the following: the code in the h2o-py module is written with the
    unicode_literals future import, and Py3 compatibility layer (which replaces some of the builtin types in Python2
    with custom objects that are Python3-compatible). However when h2o module is imported from within the "old-style"
    Python 2 environment, then this enviroment will provide h2o functions with incompatible arguments.
    For example, when Python 2 environment invokes
        h2o.connect(ip="localhost", port=12345)
    then the `ip` argument will be of "native" Python 2 str type, instead of the augmented `str` type provided by
    this module. As a result, simple check such as `isinstance(ip, str)` will fail, a

    :param fun: Function target of the decorator
    """
    from functools import wraps
    if PY3: return fun
    strings = (native_str, native_bytes, native_unicode)
    lists = (native_list, list)
    dicts = (native_dict, dict)

    def translate_list(arr):
        newarr = list(arr)  # Make sure that old-style list gets replaced with the new-style list.
        for i, a in enumerate(newarr):
            if type(a) is type: continue
            elif isinstance(a, strings): newarr[i] = str(a)
            elif isinstance(a, lists): newarr[i] = translate_list(a)
            elif isinstance(a, dicts): newarr[i] = translate_dict(a)
            elif isinstance(a, tuple): newarr[i] = tuple(translate_list(a))
        return newarr

    def translate_dict(d):
        newdict = dict()
        for k, v in viewitems(d):
            kk = str(k)
            if type(v) is type: newdict[kk] = v
            elif isinstance(v, strings): newdict[kk] = str(v)
            elif isinstance(v, lists): newdict[kk] = translate_list(v)
            elif isinstance(v, dicts): newdict[kk] = translate_dict(v)
            elif isinstance(v, tuple): newdict[kk] = tuple(translate_list(v))
            else: newdict[kk] = v
        return newdict

    @wraps(fun)
    def decorator_invisible(*args, **kwargs):
        newargs = translate_list(args)
        newkwargs = translate_dict(kwargs)
        return fun(*newargs, **newkwargs)

    return decorator_invisible
