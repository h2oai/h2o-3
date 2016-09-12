# -*- encoding: utf-8 -*-
"""
This module provides helper functions to write code that is backward-compatible.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
# Note: no unicode_literals feature, since type.__getattribute__ cannot take unicode strings as parameter...
from __future__ import division, print_function, absolute_import
from h2o.utils.compatibility import *  # NOQA


def backwards_compatible(*args):
    return with_metaclass(BackwardsCompatibleMeta, BackwardsCompatibleBase, *args)


class BackwardsCompatibleMeta(type):
    """
    Use this meta class if you have any static methods that need to be removed from the class, but at the same
    time need to still "work" to ensure backward compatibility.

    Declare `_bcsv` dictionary with variables that should be static and deprecated.

    Example:
        class A(backwards_compatible()):
            _bcsv = {"foo": 100, "nay": -1}
            _bcsm = {"getfoo": lambda: A.foo}
            def __init__(self):
                super(A, self).__init__()
    """
    def __new__(mcs, clsname, bases, dct):
        bcsv = dct.pop("_bcsv") if "_bcsv" in dct else {}
        bcsm = dct.pop("_bcsm") if "_bcsm" in dct else {}
        bcim = dct.pop("_bcim") if "_bcim" in dct else {}
        bca = set(bcsv) | set(bcsm) | set(bcim)  # Set of all "special" static names
        #print("Creating class %s with dict %r" % (clsname, dct))
        dct["_bc"] = {"sv": bcsv, "sm": bcsm, "im": bcim, "a": bca}
        return super(BackwardsCompatibleMeta, mcs).__new__(mcs, clsname, bases, dct)

    def __getattribute__(cls, name):
        bc = type.__getattribute__(cls, "_bc")
        if name in bc["a"]:
            if name in bc["sv"]:
                # print("Warning: Symbol %s in class %s is deprecated." % (name, cls.__name__))
                return bc["sv"][name]
            if name in bc["sm"]:
                # print("Warning: Method %s in class %s is deprecated." % (name, cls.__name__))
                return bc["sm"][name]
        return type.__getattribute__(cls, name)

    def __setattr__(cls, name, value):
        bc = cls.__dict__["_bc"]
        if name in bc["sv"]:
            # print("Warning: Symbol %s in class %s is deprecated." % (name, cls.__name__))
            bc["sv"][name] = value
        else:
            cls.__dict__[name] = value



class BackwardsCompatibleBase(object):
    def __init__(self):
        self._bcin = {
            # Creating lambdas in a loop, need to make sure that `fun` is bound to each lambda separately.
            name: (lambda fun: lambda *args, **kwargs: fun(self, *args, **kwargs))(fun)
            for name, fun in viewitems(self._bc["im"])
        }

    def __getattr__(self, item):
        if item in self._bcin:
            # print("Warning: Method %s in class %s is deprecated." % (item, self.__class__.__name__))
            return self._bcin[item]
        # Make sure that we look up any names not found on the instance also in the class
        return getattr(self.__class__, item)


# noinspection PyAbstractClass
class CallableString(str):
    def __call__(self):
        return self
