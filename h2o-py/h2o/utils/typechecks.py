# -*- encoding: utf-8 -*-
"""
Utilities for checking types and validity of variables.

The primary purpose of this module is to facilitate testing whether some variable has the desired type. Such testing
comes in two flavors: as the ``is_type()`` function, and the ``assert_is_type()`` assert. The latter should be used
for checking types of input variables in public methods / functions. Its advantage over simple ``assert is_type()``
construct is that: firstly it composes the error message in case of failure, and secondly it throws an
:class:`H2OTypeError` exception instead of an ``AssertionError``, which is both more precise, and more user-friendly
(in the sense that it produces much nicer error message).

General interface of this module is:

    assert_is_type(var, type1, ..., typeN)
    assert_satisfies(var, condition)
    assert_matches(var, regular_expression)
    if is_type(var, type1, ..., typeN): ...

The ``typeI`` items here deserve a more thorough explanation. They could be:

    # Plain types
    assert_is_type(flag, bool) # note that in Python ``bool`` is a subclass of ``int``
    assert_is_type(port, int)  # ``int`` and ``str`` will work on Py2 as if you were on Py3
    assert_is_type(text, str)  # (i.e. they'll also match ``long`` and ``unicode`` respectively)
    assert_is_type(hls, H2OLocalServer)
    assert_is_type(arr, list, tuple, set)
    assert_is_type(json, dict)
    assert_is_type(asdffkj, object)  # in Python ``object`` is equivalent to ``any``

    # "numeric" is a special type, meaning ``U(int, float)``
    assert_is_type(x, numeric)

    # Literals are matched by value
    assert_is_type(v, None)
    assert_is_type(scheme, "http", "https", "ftp")
    assert_is_type(dir, -1, 0, 1)

    # Testing lists
    assert_is_type(arr, [numeric])   # List of numbers
    assert_is_type(arr2, [[float]])  # List of lists of floats (i.e. a 2-dimensional array)
    assert_is_type(arr, list)        # Generic list, same as ``[object]``
    assert_is_type(arr, [int, str])  # List of either ints or strings, same as ``[U(int, str)]``

    # Sets follow the same semantic as lists, only use curly braces ``{}`` instead of square ones
    assert_is_type(s, {str})  # Set of string values
    assert_is_type(s, set)    # Generic set, same as ``{object}``

    # Tuples
    assert_is_type(t, tuple)  # any tuple
    assert_is_type(t, (int, int, int, [str]))  # Test for a 4-tuple having first 3 ints and last an array of strings
    assert_is_type(t, Tuple(int))  # tuple of ints of arbitrary length

    # Dictionaries
    assert_is_type(t, dict)  # any dictionary
    assert_is_type(cols, {str: H2OFrame})  # Same as Map<str, H2OFrame> in Java
    assert_is_type(vals, {str: U(numeric, str)})  # Dictionary with string keys and ``U(numeric, str)`` values
    # Dictionary whose key-value pairs match either ``(ktype1, vtype1)``, or ..., or ``(ktypeN, vtypeN)``
    assert_is_type(foo, {ktype1: vtype1, ..., ktypeN: vtypeN})
    # Here we test whether ``xyz`` has keys of the specified types. For example ``xyz = {"foo": 1, "bar": 2}`` will
    # pass the test, whereas ``xyz = {"foo": 0, "kaboom": None}`` will not.
    assert_is_type(xyz, {"foo": int, "bar": U(int, float, None), "baz": bool})

    # Functions and lambda-expressions
    assert_is_type(progress, I(numeric, lambda x: 0 <= x <= 1))
    assert_is_type(x, None, "N/A", I(float, math.isnan))
    assert_is_type(matrix, I([[numeric]], lambda v: all(len(vi) == len(v[0]) for vi in v)))
    assert_is_type(a, lambda t: issubclass(t, object))

As you have noticed, we define a number of special classes to facilitate type construction::

    # Union / intersection / negation
    U(str, int, float)     # denotes a type which can be either a string, or an integer, or a float
    I(Widget, Renderable)  # denotes a class which is both a Widget and a Renderable (it uses multiple inheritance)
    NOT(None)              # denotes any type except None
    # Intersection and negation are best used together:
    I(int, NOT(0))         # integer which is not zero

    # ``Tuple`` may be used to denote tuples with variable number of arguments (same as lists)
    Tuple(int)             # tuple with any number of integer elements

    # ``Dict`` is a dictionary type which should match exactly (i.e. each key must be present in tested variable)
    Dict(error=str)        # dictionary with only one key "error" with string value

    # ``BoundInt``, ``BoundNumeric`` are numbers that are bound from below below and/or above
    BoundInt(1, 100)
    BoundNumeric(0, 1)

    # Lazy class references: these types can be used anywhere without having to load the corresponding modules. Their
    # resolution is deferred until the run time, and if the module cannot be loaded no exception will be raised (but
    # of course the type check will fail).
    h2oframe          # Same as H2OFrame
    pandas_dataframe  # Same as pandas.DataFrame
    numpy_ndarray     # Same as numpy.ndarray

    # An enum. This is similar to a mere union of strings, except that we match case-insensitively
    Enum("case1", "case2", ...)


:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals
from h2o.utils.compatibility import *  # NOQA

import importlib
import io
import re
import sys
import tokenize
from types import BuiltinFunctionType, FunctionType

from h2o.exceptions import H2OTypeError, H2OValueError

__all__ = ("U", "I", "NOT", "Tuple", "Dict", "MagicType", "BoundInt", "BoundNumeric", "Enum",
           "numeric", "h2oframe", "pandas_dataframe", "numpy_ndarray", "scipy_sparse",
           "assert_is_type", "assert_matches", "assert_satisfies", "is_type")


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


def is_type(var, *args):
    """
    Return True if the variable is of the specified type(s) and False otherwise.

    This function is similar to :func:`assert_is_type`, however instead of raising an error when the variable does not
    match the provided type, it merely returns False.
    """
    return _check_type(var, U(*args))


# ----------------------------------------------------------------------------------------------------------------------
# Special types
# ----------------------------------------------------------------------------------------------------------------------

class MagicType(object):
    """Abstract "special" type."""

    def check(self, var):
        """Return True if the variable matches this type, and False otherwise."""

    def name(self, src=None):
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
        assert len(types) >= 1
        self._types = types

    def check(self, var):
        """Return True if the variable matches this type, and False otherwise."""
        return any(_check_type(var, tt) for tt in self._types)

    def name(self, src=None):
        """Return string representing the name of this type."""
        res = [_get_type_name(tt, src) for tt in self._types]
        if len(res) == 2 and "None" in res:
            res.remove("None")
            return "?" + res[0]
        else:
            return " | ".join(res)


class I(MagicType):
    """
    Intersection of types.

    We say that ``type(x) is I(type1, ..., typeN)`` if type of ``x`` is all of ``type1``, ..., ``typeN``. Arguably,
    this is much less useful concept than the union, however it may occasionally be used for classes with
    multiple inheritance.
    """

    def __init__(self, *types):
        """Create an intersection of types."""
        assert len(types) >= 1
        self._types = types

    def check(self, var):
        """Return True if the variable matches this type, and False otherwise."""
        return all(_check_type(var, tt) for tt in self._types)

    def name(self, src=None):
        """Return string representing the name of this type."""
        return " & ".join(_get_type_name(tt, src) for tt in self._types)


class NOT(MagicType):
    """
    Negation of a type.

    This type matches if and only if the variable is *not* of any of the provided types.
    """

    def __init__(self, *types):
        """Create a negation of types."""
        assert len(types) >= 1
        self._types = types

    def check(self, var):
        """Return True if the variable does not match any of the types, and False otherwise."""
        return not any(_check_type(var, tt) for tt in self._types)

    def name(self, src=None):
        """Return string representing the name of this type."""
        if len(self._types) > 1:
            return "!(%s)" % str("|".join(_get_type_name(tt, src) for tt in self._types))
        else:
            return "!" + _get_type_name(self._types[0], src)


class Tuple(MagicType):
    """Tuple of arbitrary length and having elements of same type(s)."""

    def __init__(self, *types):
        """Create a tuple of types."""
        assert len(types) >= 1
        self._element_type = types[0] if len(types) == 1 else U(*types)

    def check(self, var):
        """Return True if the variable matches this type, and False otherwise."""
        return isinstance(var, tuple) and all(_check_type(t, self._element_type) for t in var)

    def name(self, src=None):
        """Return string representing the name of this type."""
        return "(*%s)" % _get_type_name(self._element_type, src)


class Dict(MagicType):
    """
    Dictionary with strict shape signature.

    Simple dict literals can be used to specify dictionary types where keys may be optionally present, but when they
    are they should match the specified types. For example, ``{"foo": int, "bar": str}`` is a valid type for ``{}`` or
    ``{"foo": 3}`` or ``{"bar": "^_^"}`` or ``{"foo": 0, "bar": ""}``. On the other hand, ``Dict(foo=int, bar=str)``
    specifies a dictionary type where both keys "foo" and "bar" must be present and their values must be of integer
    and string types respectively.

    As a convenience, we assume that any key which is missing in the variable being tested is equivalent to
    ``value = None``, therefore if the Dict type allows for some key to be None, then it can also be missing.
    """

    def __init__(self, **kwargs):
        """Create a Dictionary object."""
        self._types = kwargs

    def check(self, var):
        """Return True if the variable matches this type, and False otherwise."""
        if not isinstance(var, dict): return False
        if any(key not in self._types for key in var): return False
        for key, ktype in viewitems(self._types):
            val = var.get(key, None)
            if not _check_type(val, ktype):
                return False
        return True

    def name(self, src=None):
        """Return string representing the name of this type."""
        return "{%s}" % ", ".join("%s: %s" % (key, _get_type_name(ktype, src))
                                  for key, ktype in viewitems(self._types))


class BoundInt(MagicType):
    """Integer type bounded from below/above."""

    def __init__(self, lb=None, ub=None):
        """
        Create a BoundInt object.

        The type will match any integer that is within the specified bounds (inclusively). Thus, ``BoundInt(0, 100)``
        matches any integer in the range from 0 to 100 (including 100). Also ``BoundInt(1)`` is a positive integer,
        and ``BoundInt(None, -1)`` is a negative integer.

        :param lb: lower bound (can be None or int)
        :param ub: upper bound (can be None or int)
        """
        self._lower_bound = lb
        self._upper_bound = ub

    def check(self, var):
        """Return True if the variable matches the specified type."""
        return (isinstance(var, _int_type) and
                (self._lower_bound is None or var >= self._lower_bound) and
                (self._upper_bound is None or var <= self._upper_bound))

    def name(self, src=None):
        """Return string representing the name of this type."""
        if self._upper_bound is None and self._lower_bound is None: return "int"
        if self._upper_bound is None:
            if self._lower_bound == 1: return "int>0"
            return "int≥%d" % self._lower_bound
        if self._lower_bound is None:
            return "int≤%d" % self._upper_bound
        return "int[%d…%d]" % (self._lower_bound, self._upper_bound)


class BoundNumeric(MagicType):
    """Numeric type bounded from below/above."""

    def __init__(self, lb=None, ub=None):
        """
        Create a BoundNumeric object.

        :param lb: lower bound (can be None or numeric)
        :param ub: upper bound (can be None or numeric)
        """
        self._lower_bound = lb
        self._upper_bound = ub

    def check(self, var):
        """Return True if the variable matches the specified type."""
        return (isinstance(var, _num_type) and
                (self._lower_bound is None or var >= self._lower_bound) and
                (self._upper_bound is None or var <= self._upper_bound))

    def name(self, src=None):
        """Return string representing the name of this type."""
        if self._upper_bound is None and self._lower_bound is None: return "numeric"
        if self._upper_bound is None: return "numeric≥%d" % self._lower_bound
        if self._lower_bound is None: return "numeric≤%d" % self._upper_bound
        return "numeric[%d…%d]" % (self._lower_bound, self._upper_bound)


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

    def __init__(self, module, symbol, checker=None):
        """Lazily load ``symbol`` from ``module``."""
        self._module = module
        self._symbol = symbol
        self._checker = checker or (lambda value, t: isinstance(value, t))
        self._name = symbol if module.startswith("h2o") else module + "." + symbol
        # Initially this is None, but will contain the class object once the class is loaded. If the class cannot be
        # loaded, this will be set to False.
        self._class = None

    def check(self, var):
        """Return True if the variable matches this type, and False otherwise."""
        if self._class is None: self._init()
        return self._class and self._checker(var, self._class)

    def _init(self):
        try:
            mod = importlib.import_module(self._module)
            self._class = getattr(mod, self._symbol, False)
        except ImportError:
            self._class = False

    def name(self, src=None):
        """Return string representing the name of this type."""
        return self._name


_enum_mangle_pattern = re.compile(r"[^a-z]+")
def _enum_mangle(var):
    return _enum_mangle_pattern.sub("", var.lower())

class Enum(MagicType):
    """
    Enum-like type, however values are matched case-insensitively.
    """

    def __init__(self, *consts):
        """Initialize the Enum."""
        self._consts = set(_enum_mangle(c) for c in consts)

    def check(self, var):
        """Check whether the provided value is a valid enum constant."""
        if not isinstance(var, _str_type): return False
        return _enum_mangle(var) in self._consts

    def name(self, src=None):
        """Return string representing the name of this type."""
        return "Enum[%s]" % ", ".join('"%s"' % c for c in self._consts)



numeric = U(int, float)
"""Number, either integer or real."""

h2oframe = _LazyClass("h2o", "H2OFrame")
pandas_dataframe = _LazyClass("pandas", "DataFrame")
pandas_timestamp = _LazyClass("pandas", "Timestamp")
numpy_ndarray = _LazyClass("numpy", "ndarray")
numpy_datetime = _LazyClass("numpy", "datetime64")
scipy_sparse = _LazyClass("scipy.sparse", "issparse", lambda value, t: t(value))


#-----------------------------------------------------------------------------------------------------------------------
# Asserts
#-----------------------------------------------------------------------------------------------------------------------

def assert_is_type(var, *types, **kwargs):
    """
    Assert that the argument has the specified type.

    This function is used to check that the type of the argument is correct, otherwises it raises an H2OTypeError.
    See more details in the module's help.

    :param var: variable to check
    :param types: the expected types
    :param kwargs:
        message: override the error message
        skip_frames: how many local frames to skip when printing out the error.

    :raises H2OTypeError: if the argument is not of the desired type.
    """
    assert types, "The list of expected types was not provided"
    expected_type = types[0] if len(types) == 1 else U(*types)
    if _check_type(var, expected_type): return

    # Type check failed => Create a nice error message
    assert set(kwargs).issubset({"message", "skip_frames"}), "Unexpected keyword arguments: %r" % kwargs
    message = kwargs.get("message", None)
    skip_frames = kwargs.get("skip_frames", 1)
    args = _retrieve_assert_arguments()
    vname = args[0]
    etn = _get_type_name(expected_type, dump=", ".join(args[1:]))
    vtn = _get_type_name(type(var))
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
            with io.open(fr.f_code.co_filename, "r", encoding="utf-8") as f:
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
        # ``vtype`` is a name of the class, or a built-in type such as "list", "tuple", etc
        return isinstance(var, vtype)
    if isinstance(vtype, list):
        # ``vtype`` is a list literal
        elem_type = U(*vtype)
        return isinstance(var, list) and all(_check_type(item, elem_type) for item in var)
    if isinstance(vtype, set):
        # ``vtype`` is a set literal
        elem_type = U(*vtype)
        return isinstance(var, set) and all(_check_type(item, elem_type) for item in var)
    if isinstance(vtype, tuple):
        # ``vtype`` is a tuple literal
        return (isinstance(var, tuple) and len(vtype) == len(var) and
                all(_check_type(var[i], vtype[i]) for i in range(len(vtype))))
    if isinstance(vtype, dict):
        # ``vtype`` is a dict literal
        ttkv = U(*viewitems(vtype))
        return isinstance(var, dict) and all(_check_type(kv, ttkv) for kv in viewitems(var))
    if isinstance(vtype, (FunctionType, BuiltinFunctionType)):
        return vtype(var)
    raise RuntimeError("Ivalid type %r in _check_type()" % vtype)


def _get_type_name(vtype, dump=None):
    """
    Return the name of the provided type.

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
    if is_type(vtype, str):
        return '"%s"' % repr(vtype)[1:-1]
    if is_type(vtype, int):
        return str(vtype)
    if isinstance(vtype, MagicType):
        return vtype.name(dump)
    if isinstance(vtype, type):
        return vtype.__name__
    if isinstance(vtype, list):
        return "list(%s)" % _get_type_name(U(*vtype), dump)
    if isinstance(vtype, set):
        return "set(%s)" % _get_type_name(U(*vtype), dump)
    if isinstance(vtype, tuple):
        return "(%s)" % ", ".join(_get_type_name(item, dump) for item in vtype)
    if isinstance(vtype, dict):
        return "dict(%s)" % ", ".join("%s: %s" % (_get_type_name(tk, dump), _get_type_name(tv, dump))
                                      for tk, tv in viewitems(vtype))
    if isinstance(vtype, (FunctionType, BuiltinFunctionType)):
        if vtype.__name__ == "<lambda>":
            return _get_lambda_source_code(vtype, dump)
        else:
            return vtype.__name__
    raise RuntimeError("Unexpected `vtype`: %r" % vtype)


def _get_lambda_source_code(lambda_fn, src):
    """Attempt to find the source code of the ``lambda_fn`` within the string ``src``."""
    def gen_lambdas():
        def gen():
            yield src + "\n"

        g = gen()
        step = 0
        tokens = []
        for tok in tokenize.generate_tokens(getattr(g, "next", getattr(g, "__next__", None))):
            if step == 0:
                if tok[0] == tokenize.NAME and tok[1] == "lambda":
                    step = 1
                    tokens = [tok]
                    level = 0
            elif step == 1:
                if tok[0] == tokenize.NAME:
                    tokens.append(tok)
                    step = 2
                else:
                    step = 0
            elif step == 2:
                if tok[0] == tokenize.OP and tok[1] == ":":
                    tokens.append(tok)
                    step = 3
                else:
                    step = 0
            elif step == 3:
                if level == 0 and (tok[0] == tokenize.OP and tok[1] in ",)" or tok[0] == tokenize.ENDMARKER):
                    yield tokenize.untokenize(tokens).strip()
                    step = 0
                else:
                    tokens.append(tok)
                    if tok[0] == tokenize.OP:
                        if tok[1] in "[({": level += 1
                        if tok[1] in "])}": level -= 1
        assert not tokens

    actual_code = lambda_fn.__code__.co_code
    for lambda_src in gen_lambdas():
        try:
            fn = eval(lambda_src, globals(), locals())
            if fn.__code__.co_code == actual_code:
                return lambda_src.split(":", 1)[1].strip()
        except Exception:
            pass
    return "<lambda>"
