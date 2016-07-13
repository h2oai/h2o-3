#!/usr/bin/env python
# -*- encoding: utf-8 -*-
#
# Copyright 2016 H2O.ai;  Apache License Version 2.0 (see LICENSE for details)
#
"""
This module gathers common declarations needed to ensure Python 2 / Python 3 compatibility.
It has to be imported from all other files, so that the common header looks like this:

from __future__ import division, print_function, absolute_import, unicode_literals
# noinspection PyUnresolvedReferences
from .compatibility import *

"""
from __future__ import division, print_function, absolute_import, unicode_literals
# noinspection PyUnresolvedReferences
from future.utils import PY2, PY3, with_metaclass
from functools import wraps

# Store original type declarations, in case we need them later
native_bytes = bytes
native_dict = dict
native_int = int
native_list = list
native_object = object
native_str = str
if PY2:
    # "unicode" symbol doesn't exist in PY3
    native_unicode = unicode

#
# Types
#
if PY2:
    # noinspection PyUnresolvedReferences, PyShadowingBuiltins
    from future.types import (newbytes as bytes,
                              newdict as dict,
                              newint as int,
                              newlist as list,
                              newobject as object,
                              newrange as range,
                              newstr as str)
#
# Iterators
#
if PY2:
    # noinspection PyUnresolvedReferences
    from future.builtins.iterators import (range, filter, map, zip)
if PY2 or PY3:
    # noinspection PyUnresolvedReferences
    from future.utils import (viewitems, viewkeys, viewvalues)

#
# Disabled functions
#   -- attempt to use any of these functions will raise an AssertionError now!
#
if PY2:
    # noinspection PyUnresolvedReferences
    from future.builtins.disabled import (apply, cmp, coerce, execfile, file, long, raw_input,
                                          reduce, reload, unicode, xrange, StandardError)

#
# Miscellaneous
#
if PY2:
    # noinspection PyUnresolvedReferences
    from future.builtins.misc import (ascii, chr, hex, input, next, oct, open, pow, round, super)


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
