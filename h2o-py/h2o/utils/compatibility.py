# -*- encoding: utf-8 -*-
"""
Python 2 / 3 compatibility module.

This module gathers common declarations needed to ensure Python 2 / Python 3 compatibility.
It has to be imported from all other files, so that the common header looks like this:

from h2o.utils.compatibility import *  # NOQA

------------------------------------------------------------------------------------------------------------------------
1. Strings
    In Py2 `str` is a byte string (`bytes is str == True`), and `unicode` is a unicode string.
    In Py3 `str` is a unicode string, `bytes` is a byte string, and symbol `unicode` is not defined.
    Iterating over a `bytes` string in Py2 produces characters, in Py3 character codes.

    For consistent results, use
        test_type(s, str)  to test whether an argument is a string
        bytes_iterator(s)  to iterate over byte-codes of strins s (which could be bytes or unicode)

2. Integers
    In Py2 there are two integer types: `int` and `long`. The latter has suffix "L" when stringified with repr().
    In Py3 `int` is a single integer type, `long` doesn't exist.

    For consistent results, use
        test_type(x, int)  to test whether an argument is an integer
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

__all__ = ("bytes_iterator",
           "apply", "cmp", "coerce", "execfile", "file", "long", "raw_input", "reduce", "reload", "unicode", "xrange",
           "StandardError", "csv_dict_writer", 
           "str_type", "repr2", 'str2', "bytes2", "PList", 'get_builtin', 'set_builtin')


#-----------------------------------------------------------------------------------------------------------------------
# Iterators
#-----------------------------------------------------------------------------------------------------------------------
if True:

    def next_method(gen):
        """Return the 'next' method of the given generator."""
        return gen.__next__


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


# ----------------------------------------------------------------------------------------------------------------------
# Miscellaneous
# ----------------------------------------------------------------------------------------------------------------------

def csv_dict_writer(f, fieldnames, **kwargs):
    """Equivalent of csv.DictWriter, but allows `delimiter` to be a unicode string on Py2."""
    import csv
    if "delimiter" in kwargs:
        kwargs["delimiter"] = str(kwargs["delimiter"])
    return csv.DictWriter(f, fieldnames, **kwargs)


def bytes_iterator(s):
    """Given a string, return an iterator over this string's bytes (as ints)."""
    if s is None: return
    if isinstance(s, str):
        for ch in s:
            yield ord(ch)
    elif isinstance(s, bytes):
        for ch in s:
            yield ch
    else:
        raise TypeError("String argument expected, got %s" % type(s))


def repr2(x):
    """Analogous to repr(), but will suppress 'u' prefix when repr-ing a unicode string."""
    s = repr(x)
    if len(s) >= 2 and s[0] == "u" and (s[1] == "'" or s[1] == '"'):
        s = s[1:]
    return s


str_type = str2 = str
bytes2 = bytes
    

def get_builtin(fn_name):
    import builtins
    return getattr(builtins, fn_name, None)
    

def set_builtin(name, value):
    import builtins
    setattr(builtins, name, value)
    

class PList(list):
    """
    Wrapper for printable lists ensuring that the list is printed/represented the same way in Py2 and Py3.
    Use with caution: in PY2, this will work only if all items in the list are ascii-compatible.
    Mainly aimed for usage in docstrings or warnings where list items are defined statically.
    """
    
    def __init__(self, arr):
        super(PList, self).__init__(arr)
        
    def __str__(self):
        return str([it for it in self])
    
    def __repr__(self):
        return repr([it for it in self])
    

