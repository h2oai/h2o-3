# -*- encoding: utf-8 -*-
"""
This module provides helper functions to write code that is backward-compatible.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
# Note: no unicode_literals feature, since type.__getattribute__ cannot take unicode strings as parameter...
from __future__ import division, print_function, absolute_import

from functools import wraps
import warnings

from h2o.utils.compatibility import *  # NOQA


def h2o_meta(*args):
    return with_metaclass(H2OMeta, *args)


def fullname(fn):
    """for compatibility with Py 2.7"""
    return fn.__qualname__ if hasattr(fn, '__qualname__') else fn.__name__


class MetaFeature(object):

    NOT_FOUND = object()

    @classmethod
    def before_class(cls, bases, dct):
        """
        Allows to dynamically change how the class will be constructed
        """
        return bases, dct

    @classmethod
    def after_class(cls, clz):
        return clz

    @classmethod
    def get_class_attr(cls, clz, name):
        return MetaFeature.NOT_FOUND

    @classmethod
    def set_class_attr(cls, clz, name, value):
        return False

    @staticmethod
    def type_attr(clz, name):
        try:
            return type.__getattribute__(clz, name)
        except AttributeError:
            return None



class Alias(MetaFeature):

    @classmethod
    def before_class(cls, bases, dct):
        attr_names = set(dct)
        for name, impl in dct.items():
            if hasattr(impl, '_aliases'):
                for alias in impl._aliases - attr_names:
                    dct[str(alias)] = impl
                delattr(impl, '_aliases')
        return bases, dct

    def __init__(self, *aliases):
        self._aliases = set(aliases)

    def __call__(self, fn):
        fn._aliases = self._aliases
        return fn


class Deprecated(object):

    def __init__(self, msg=None, replaced_by=None):
        self._msg = msg
        self._replaced_by = replaced_by

    def __call__(self, fn):
        msg = self._msg if self._msg is not None \
            else "{} is deprecated, please use ``{}`` instead.".format(fullname(fn), fullname(self._replaced_by)) if self._replaced_by is not None \
            else "{} is deprecated.".format(fullname(fn))
        fn.__doc__ = "{msg}\n\n{doc}".format(msg=msg, doc=fn.__doc__) if fn.__doc__ is not None else msg
        call_fn = self._replaced_by or fn

        @wraps(fn)
        def wrapper(*args, **kwargs):
            warnings.warn(msg, DeprecationWarning, 2)
            return call_fn(*args, **kwargs)

        return wrapper


class BackwardsCompatible(MetaFeature):

    @staticmethod
    def make_getattr(clz,):
        ori_getattr_ = clz.__getattr__ if MetaFeature.type_attr(clz, '__getattr__') else None

        def __getattr__(self, name):
            if ori_getattr_:
                try:
                    return ori_getattr_(self, name)
                except AttributeError:
                    pass
            if name in self._bc:
                return self._bc[name]
            return getattr(clz, name)

        return __getattr__

    @staticmethod
    def make_new(clz):
        def new_inst(cls, *args, **kwargs):
            inst = super(clz, cls).__new__(cls, *args, **kwargs)
            inst._bc = {name: (lambda fn: lambda *args, **kwargs: fn(inst, *args, **kwargs))(fn)
                        for name, fn in cls._bc._instance_attrs.items()}
            return inst
        return new_inst

    def __init__(self, class_attrs=None, instance_attrs=None):
        self._class_attrs = class_attrs or {}
        self._instance_attrs = instance_attrs or {}

    def __call__(self, clz):
        clz._bc = self
        clz.__new__ = staticmethod(BackwardsCompatible.make_new(clz))
        clz.__getattr__ = BackwardsCompatible.make_getattr(clz)

        # attr_names = set(dir(clz))
        # for name, impl in self._static_attrs.items():
        #     if name not in attr_names:
        #         setattr(clz, name, staticmethod(impl) if callable(impl) else impl)
        return clz

    @classmethod
    def get_class_attr(cls, clz, name):
        bc = cls.type_attr(clz, '_bc')
        if bc is not None and name in bc._class_attrs:
            return bc._class_attrs[name]
        return super(BackwardsCompatible, cls).get_class_attr(clz, name)

    @classmethod
    def set_class_attr(cls, clz, name, value):
        bc = cls.type_attr(clz, '_bc')
        if bc is not None and name in bc._class_attrs:
            bc._class_attrs[name] = value
            return True
        return super(BackwardsCompatible, cls).set_class_attr(clz, name, value)


class H2OMeta(type):

    _FEATURES = [Alias, BackwardsCompatible]

    def __new__(mcs, name, bases, dct):
        for m in H2OMeta._FEATURES:
            bases, dct = m.before_class(bases, dct)
        clz = super(H2OMeta, mcs).__new__(mcs, name, bases, dct)
        for m in H2OMeta._FEATURES:
            clz = m.after_class(clz)
        return clz

    def __getattribute__(cls, name):
        for m in H2OMeta._FEATURES:
            attr = m.get_class_attr(cls, name)
            if attr is not MetaFeature.NOT_FOUND:
                return attr
        return type.__getattribute__(cls, name)

    def __setattr__(cls, name, value):
        for m in H2OMeta._FEATURES:
            if m.set_class_attr(cls, name, value):
                return
        type.__setattr__(cls, name, value)


# noinspection PyAbstractClass
class CallableString(str):
    def __call__(self):
        return self
