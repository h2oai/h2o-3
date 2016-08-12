# -*- encoding: utf-8 -*-
"""
Utilities for checking types and validity of variables.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals

import re
import sys
import tokenize

from h2o.utils.compatibility import *  # NOQA
from h2o.exceptions import H2OTypeError, H2OValueError

__all__ = ("U", "I", "numeric", "h2oframe", "pandas_dataframe", "numpy_ndarray",
           "assert_is_type", "assert_matches", "assert_satisfies", "test_type")


if PY2:
    # noinspection PyProtectedMember
    from h2o.utils.compatibility import _native_unicode, _native_long
    _str_type = (str, _native_unicode)
    _int_type = (int, _native_long)
    _num_type = (int, _native_long, float)
    _primitive_type = (str, int, float, bool, _native_unicode, _native_long)
else:
    _str_type = str
    _int_type = int
    _num_type = (int, float)
    _primitive_type = (str, int, float, bool, bytes)




def test_type(var, *args):
    """
    Return True if the variable is of the specified type(s) and False otherwise.

    This function is similar to :func:`assert_is_type`, however instead of raising an error when the variable does not
    match the provided type, it merely returns False.
    """
    return _check_type(var, U(*args))


#-----------------------------------------------------------------------------------------------------------------------
# Special types
#-----------------------------------------------------------------------------------------------------------------------

class MagicType(object):
    """Abstract "special" type."""

    def check(self, var):
        """Return True if the variable matches this type, and False otherwise."""

    def __str__(self):
        """Return string representing the name of this type."""
        return "<%s>" % self.__class__.__name__


class U(MagicType):
    """
    Union of types.

    We say that ``type(x) is U(type1, ..., typeN)`` if type of ``x`` is one of ``type1``, ..., ``typeN``.

    This is a helper class that can be used in situations where unions are otherwise hard or impossible to declare.
    For example, one doesn't need a union type to in the test ``assert_is_type(x, int, str, None)``, but if we want to
    have unions as dictionary keys we can say: ``assert_is_type(d, {str: U(int, bool, None)})``.
    """

    def __init__(self, *types):
        """Create an object representing the union of ``*types``."""
        self._types = types

    def check(self, var):
        """Return True if the variable matches this type, and False otherwise."""
        return any(_check_type(var, tt) for tt in self._types)

    def __str__(self):
        res = [_get_type_name(tt) for tt in self._types]
        if len(res) == 2 and "None" in res:
            res.remove("None")
            return "?" + res[0]
        else:
            return "|".join(res)


class I(MagicType):
    """
    Intersection of types.

    We say that ``type(x) is I(type1, ..., typeN)`` if type of ``x`` is all of ``type1``, ..., ``typeN``. Arguably,
    this is much less useful concept than the union, however it may occasionally be used for classes with
    multiple inheritance.
    """

    def __init__(self, *types):
        """Create an intersection of types."""
        self._types = types

    def check(self, var):
        """Return True if the variable matches this type, and False otherwise."""
        return all(_check_type(var, tt) for tt in self._types)

    def __str__(self):
        return "&".join(_get_type_name(tt) for tt in self._types)


class _LazyClass(MagicType):
    """
    Helper class for lazy (on-demand) loading of some external classes.

    The purpose of this class is to provide a way of testing against classes that either cannot be loaded immediately,
    or may not even be present on user's system. This class must be taught how to load each class of interest, and
    therefore should not be exposed to the user code. Instead use "singletons" ``h2oframe``, ``pandas_dataframe``,
    ``numpy_ndarray`` (and perhaps others in future). Usage::

        from h2o.utils.typechecks import assert_is_type, h2oframe, pandas_dataframe, numpy_ndarray
        assert_is_type(fr, h2oframe, pandas_dataframe, numpy_ndarray)
    """

    def __init__(self, name):
        assert name in {"H2OFrame", "pandas.DataFrame", "numpy.ndarray"}
        self._name = name
        # Initially this is None, but will contain the class object once the class is loaded. If the class cannot be
        # loaded, this will be set to False.
        self._class = None

    def check(self, var):
        """Return True if the variable matches this type, and False otherwise."""
        if self._class is None: self._init()
        return self._class and isinstance(var, self._class)

    def _init(self):
        if self._name == "H2OFrame":
            from h2o import H2OFrame
            self._class = H2OFrame
        elif self._name == "pandas.DataFrame":
            try:
                # noinspection PyUnresolvedReferences
                from pandas import DataFrame
                self._class = DataFrame
            except ImportError:
                self._class = False
        elif self._name == "numpy.ndarray":
            try:
                # noinspection PyUnresolvedReferences
                from numpy import ndarray
                self._class = ndarray
            except ImportError:
                self._class = False

    def __str__(self):
        return self._name




numeric = U(int, float)
"""Number, either integer or real."""

h2oframe = _LazyClass("H2OFrame")
pandas_dataframe = _LazyClass("pandas.DataFrame")
numpy_ndarray = _LazyClass("numpy.ndarray")


#-----------------------------------------------------------------------------------------------------------------------
# Asserts
#-----------------------------------------------------------------------------------------------------------------------

def assert_is_type(var, *types, **kwargs):
    """
    Assert that the argument has the specified type.

    This function is used to check that the type of the argument is correct, otherwises it raises an H2OTypeError.
    The following use cases are supported::

        # simple check
        assert_is_type(flag, bool)
        assert_is_type(fr, H2OFrame)
        assert_is_type(arr, list)

        # this works as expected (even though None is not a type): asserts that v is None
        assert_is_type(v, None)

        # ``int`` and ``str`` are special-cased to work on Py2 same way as on Py3
        assert_is_type(x, int)
        assert_is_type(y, str)

        # ``numeric`` is a special type, meaning ``U(int, float)``
        assert_is_type(x, numeric)

        # check for a variable that may have multiple different types
        assert_is_type(ip, None, str)
        assert_is_type(x, int, float, str, None)
        assert_is_type(x, U(int, float, str, None))
        assert_is_type(scheme, "http", "https", "ftp")
        assert_is_type(dir, -1, 0, 1)

        # check for a list of ints or set of ints
        assert_is_type(arr, [int], {int})

        # check for a 2-dimensional array of numeric variables
        assert_is_type(arr2, [[numeric]])

        # check for a dictionary<str, H2OFrame>
        assert_is_type(cols, {str: H2OFrame})

        # check for a dictionary<str, int|float>
        assert_is_type(vals, {str: U(int, float)})

        # check for a struct with the specific shape
        assert_is_type({"foo": 1, "bar": 2}, {"foo": int, "bar": U(int, float, None), "baz": bool})

        # check for a tuple with the specific type signature
        assert_is_type(t, (int, int, int, [str]))

    Note that in Python everything is an ``object``, so you can use "object" to mean "any".

    :param var: variable to check
    :param types: the expected types
    :param kwargs:
        message: override the error message
        skip_frames: how many local frames to skip when printing out the error.

    :raises H2OTypeError: if the argument is not of the desired type.
    """
    assert types, "The list of expected types was not provided"
    if len(types) == 1:
        if _check_type(var, types[0]): return
        etn = _get_type_name(types[0])
    else:
        union_type = U(*types)
        if _check_type(var, union_type): return
        etn = str(union_type)
    assert set(kwargs).issubset({"message", "skip_frames"}), "Unexpected keyword arguments: %r" % kwargs
    vname = _retrieve_assert_arguments()[0]
    message = kwargs.get("message", None)
    skip_frames = kwargs.get("skip_frames", 1)
    vtn = _get_type_name([type(var)])
    raise H2OTypeError(var_name=vname, var_value=var, var_type_name=vtn, exp_type_name=etn, message=message,
                       skip_frames=skip_frames)



def assert_matches(v, regex):
    """
    Assert that string variable matches the provided regular expression.

    :param v: variable to check.
    :param regex: regular expression to check against (can be either a string, or compiled regexp).
    """
    m = re.match(regex, v)
    if m is None:
        vn = _retrieve_assert_arguments()[0]
        message = "Argument `{var}` (= {val!r}) did not match /{regex}/".format(var=vn, regex=regex, val=v)
        raise H2OValueError(message, var_name=vn, skip_frames=1)
    return m


def assert_satisfies(v, cond, message=None):
    """
    Assert that variable satisfies the provided condition.

    :param v: variable to check. Its value is only used for error reporting.
    :param bool cond: condition that must be satisfied. Should be somehow related to the variable ``v``.
    :param message: message string to use instead of the default.
    """
    if not cond:
        vname, vexpr = _retrieve_assert_arguments()
        if not message:
            message = "Argument `{var}` (= {val!r}) does not satisfy the condition {expr}" \
                      .format(var=vname, val=v, expr=vexpr)
        raise H2OValueError(message=message, var_name=vname, skip_frames=1)



#-----------------------------------------------------------------------------------------------------------------------
# Implementation details
#-----------------------------------------------------------------------------------------------------------------------

def _retrieve_assert_arguments():
    """
    Magic variable name retrieval.

    This function is designed as a helper for assert_is_type() function. Typically such assertion is used like this::

        assert_is_type(num_threads, int)

    If the variable `num_threads` turns out to be non-integer, we would like to raise an exception such as

        H2OTypeError("`num_threads` is expected to be integer, but got <str>")

    and in order to compose an error message like that, we need to know that the variables that was passed to
    assert_is_type() carries a name "num_threads". Naturally, the variable itself knows nothing about that.

    This is where this function comes in: we walk up the stack trace until the first frame outside of this
    file, find the original line that called the assert_is_type() function, and extract the variable name from
    that line. This is slightly fragile, in particular we assume that only one assert_is_type statement can be per line,
    or that this statement does not spill over multiple lines, etc.
    """
    try:
        raise RuntimeError("Catch me!")
    except RuntimeError:
        # Walk up the stacktrace until we are outside of this file
        tb = sys.exc_info()[2]
        assert tb.tb_frame.f_code.co_name == "_retrieve_assert_arguments"
        this_filename = tb.tb_frame.f_code.co_filename
        fr = tb.tb_frame
        while fr is not None and fr.f_code.co_filename == this_filename:
            fr = fr.f_back

        # Read the source file and tokenize it, extracting the expressions.
        try:
            with open(fr.f_code.co_filename, "r") as f:
                # Skip initial lines that are irrelevant
                for i in range(fr.f_lineno - 1): next(f)
                # Create tokenizer
                g = tokenize.generate_tokens(f.readline)
                step = 0
                args_tokens = []
                level = 0
                for ttt in g:
                    if step == 0:
                        if ttt[0] != tokenize.NAME: continue
                        if not ttt[1].startswith("assert_"): continue
                        step = 1
                    elif step == 1:
                        assert ttt[0] == tokenize.OP and ttt[1] == "("
                        args_tokens.append([])
                        step = 2
                    elif step == 2:
                        if level == 0 and ttt[0] == tokenize.OP and ttt[1] == ",":
                            args_tokens.append([])
                        elif level == 0 and ttt[0] == tokenize.OP and ttt[1] == ")":
                            break
                        else:
                            if ttt[0] == tokenize.OP and ttt[1] in "([{": level += 1
                            if ttt[0] == tokenize.OP and ttt[1] in ")]}": level -= 1
                            assert level >= 0, "Parse error: parentheses level became negative"
                            args_tokens[-1].append(ttt)
                args = [tokenize.untokenize(at).strip().replace("\n", " ") for at in args_tokens]
                return args
        except IOError:
            return "arg",


def _check_type(var, vtype):
    """
    Return True if the variable is of the specified type, and False otherwise.

    :param var: variable to check
    :param vtype: expected variable's type
    """
    if vtype is None:
        return var is None
    if isinstance(vtype, _primitive_type):
        return var == vtype
    if vtype is str:
        return isinstance(var, _str_type)
    if vtype is int:
        return isinstance(var, _int_type)
    if vtype is numeric:
        return isinstance(var, _num_type)
    if isinstance(vtype, MagicType):
        return vtype.check(var)
    if isinstance(vtype, type):
        return isinstance(var, vtype)
    if isinstance(vtype, list):
        elem_type = U(*vtype)
        return isinstance(var, list) and all(_check_type(item, elem_type) for item in var)
    if isinstance(vtype, set):
        elem_type = U(*vtype)
        return isinstance(var, set) and all(_check_type(item, elem_type) for item in var)
    if isinstance(vtype, tuple):
        return (isinstance(var, tuple) and len(vtype) == len(var) and
                all(_check_type(var[i], vtype[i]) for i in range(len(vtype))))
    if isinstance(vtype, dict):
        ttkv = U(*viewitems(vtype))
        return isinstance(var, dict) and all(_check_type(kv, ttkv) for kv in viewitems(var))
    raise RuntimeError("Ivalid type %r in _check_type()" % vtype)


def _get_type_name(vtype):
    """
    Return the name of the provided type(s).

        _get_type_name(int) == "integer"
        _get_type_name(str) == "string"
        _get_type_name(tuple) == "tuple"
        _get_type_name(Exception) == "Exception"
        _get_type_name(U(int, float, bool)) == "integer|float|bool"
        _get_type_name(U(H2OFrame, None)) == "?H2OFrame"
    """
    if vtype is None:
        return "None"
    if vtype is str:
        return "string"
    if vtype is int:
        return "integer"
    if vtype is numeric:
        return "numeric"
    if test_type(vtype, str):
        return '"%s"' % repr(vtype)[1:-1]
    if test_type(vtype, int):
        return str(vtype)
    if isinstance(vtype, MagicType):
        return str(vtype)
    if isinstance(vtype, type):
        return vtype.__name__
    if isinstance(vtype, list):
        return "list(%s)" % _get_type_name(U(*vtype))
    if isinstance(vtype, set):
        return "set(%s)" % _get_type_name(U(*vtype))
    if isinstance(vtype, tuple):
        return "(%s)" % ", ".join(_get_type_name(item) for item in vtype)
    if isinstance(vtype, dict):
        return "dict(%s)" % ", ".join("%s: %s" % (_get_type_name(tk), _get_type_name(tv))
                                      for tk, tv in viewitems(vtype))
    raise RuntimeError("Unexpected `vtype`: %r" % vtype)
