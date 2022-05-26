# -*- encoding: utf-8 -*-
"""
This module provides helper functions to write code that is backward-compatible.

:copyright: (c) 2016 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
# Note: no unicode_literals feature, since type.__getattribute__ cannot take unicode strings as parameter...
from __future__ import division, print_function, absolute_import
from h2o.utils.compatibility import *  # NOQA

from functools import wraps
import inspect
import warnings

from h2o.exceptions import H2ODeprecationWarning


def h2o_meta(*args):
    return with_metaclass(H2OMeta, *args)


def fullname(fn):
    """for compatibility with Py 2.7"""
    return fn.__qualname__ if hasattr(fn, '__qualname__') else fn.__name__


def extend_and_replace(cls, **attrs):
    new_attrs = dict(cls.__dict__)
    new_attrs.update(attrs)
    new_cls = type(cls.__name__, (cls,), new_attrs)
    # new_cls.__module__ = cls.__module__
    # setattr(sys.modules[cls.__module__], cls.__name__, new_cls)
    return new_cls


def decoration_info(fn):
    return getattr(fn, '__decoration__', None)


def _set_decoration_info(wrapper, wrapped, decoration_type):
    wrapper.__decoration__ = dict(
        wrapped=wrapped,
        type=decoration_type
    )
    

def deprecated_params(deprecations):
    old = deprecations.keys()
    
    def decorator(fn):
        fn_name = fullname(fn)
        
        @wraps(fn)
        def wrapper(*args, **kwargs):
            new_kwargs = {}
            keys = set(kwargs.keys())
            messages = []
            for k, v in kwargs.items():
                if k in old:
                    new = deprecations[k]
                    new_tup = (((lambda ov: None), None) if new in [None, ()] 
                               else ((lambda ov: {new: ov}), None) if isinstance(new, str_type)
                               else (new, None) if callable(new)
                               else ((lambda ov: None), new[1]) if isinstance(new, tuple) and new[0] is None
                               else ((lambda ov: {new[0]: ov}), new[1]) if isinstance(new, tuple) and isinstance(new[0], str_type)
                               else new)
                    assert isinstance(new_tup, tuple), (
                            "`deprecations` values must be one of: "
                            "None (deprecated param removed), a string (deprecated property renamed), "
                            "a tuple(new_name: Optional[str], message: str) to customize the deprecation message, "
                            "a callable lambda old_value: dict(param1=value1, param2=value2) for advanced deprecations "
                            "(one param replaced with one or more params with transformation of the deprecated value), "
                            "or a tuple(lambda old_value: dict(param1=value1, param2=value2), message: str).")
                    transform_fn, msg = new_tup
                    new_params = transform_fn(v)
                    if new_params in [None, {}]:
                        messages.append(msg or "``{}`` param of ``{}`` is deprecated and will be ignored."
                                               .format(k, fn_name))
                    else:
                        assert isinstance(new_params, dict)
                        messages.append(msg or "``{}`` param of ``{}`` is deprecated, please use ``{}`` instead."
                                               .format(k, fn_name, ', '.join(new_params.keys())))
                        intersect = set(new_params.keys()) & keys
                        if any(intersect):
                            messages.append("Using both deprecated param ``{}`` and new param(s) ``{}`` in call to ``{}``, "
                                            "the deprecated param will be ignored."
                                            .format(k, ', '.join(intersect), fn_name))
                        else:
                            new_kwargs.update(new_params)
                else:
                    new_kwargs[k] = v
            for msg in messages:
                warnings.warn(msg, H2ODeprecationWarning, 2)
            return fn(*args, **new_kwargs)
    
        _set_decoration_info(wrapper, fn, 'deprecation')
    
        return wrapper
    
    return decorator
        

def deprecated_property(name, replaced_by=None, message=None):
    """
    Creates a deprecated property that forwards logic to `replaced_by` property.
    :param name: name of the deprecated property.
    :param replaced_by: the new property object. If None, then the deprecated property will be a no-op property.
    :param message: the custom deprecation message. If None, a default message will be used.
    :return: the deprecated property.
    """
    
    if replaced_by:
        new_name = replaced_by.fget.__name__
        doc = message or "[Deprecated] Use ``{}`` instead".format(new_name)
        # doc += "\n\n{}".format(replaced_by.__doc__)
        msg = message or "``{}`` is deprecated, please use ``{}`` instead.".format(name, new_name)
        
        def wrap(accessor):
            if accessor is None: return 
            
            def wrapper(*args):
                warnings.warn(msg, H2ODeprecationWarning, 2)
                return accessor(*args)
            return wrapper
        
        return property(wrap(replaced_by.fget), wrap(replaced_by.fset), wrap(replaced_by.fdel), doc)
    else:
        doc = message or "[Deprecated] The property was removed and will be ignored."
        msg = message or "``{}`` is deprecated and will be ignored.".format(name)
        
        def _fget(self):
            warnings.warn(msg, H2ODeprecationWarning, 2)
            return None
            
        def _fset(self, _):
            warnings.warn(msg, H2ODeprecationWarning, 2)

        return property(_fget, _fset, None, doc)


class _DeprecatedFunction(object):
    """
    Decorator for deprecated functions or methods.

    :example::
        class Foo:

            def new_method(self, param=None):
                ...
                do_sth(param)

            @deprecated_function(replaced_by=new_method)
            def old_method(self, param=None):
                pass
    """

    def __init__(self, msg=None, replaced_by=None):
        """
        :param msg: the deprecation message to print as a ``DeprecationWarning`` when the function is called.
        :param replaced_by: the optional function replacing the deprecated one.
            If provided, then the code from the legacy method can be deleted and limited to `pass`,
            as the call, with its arguments, will be automatically forwarded to this replacement function.
        """
        self._msg = msg
        self._replaced_by = replaced_by

    def __call__(self, fn):
        msg = (self._msg if self._msg is not None
               else "``{}`` is deprecated, please use ``{}`` instead."
                    .format(fullname(fn), fullname(self._replaced_by)) 
                    if self._replaced_by is not None
               else "``{}`` is deprecated.".format(fullname(fn)))
        fn.__doc__ = "{msg}\n\n{doc}".format(msg=msg, doc=fn.__doc__) if fn.__doc__ is not None else msg
        call_fn = self._replaced_by or fn

        @wraps(fn)
        def wrapper(*args, **kwargs):
            warnings.warn(msg, H2ODeprecationWarning, 2)
            return call_fn(*args, **kwargs)

        return wrapper


deprecated_fn = _DeprecatedFunction


def deprecated_params_order(old_sig, is_called_with_old_sig):
    """
    Creates a deprecated property order and provide a correct function call
    :param old_sig: list of strings in old property order
    :param is_called_with_old_sig: Function that return true if the function is called with different order
    :return: function call with correct parameter order and deprecation warning or the same function call

    :example::

        def _is_called_with_old_sig(*args, **kwargs): return len(args) > 0 and isinstance(args[0], bool)

        class Foo:

            @deprecated_params_order(old_sig=["param2", "param1"], is_called_with_old_sig=_is_called_with_old_sig)
            def method(self, param1, param2):
                pass
    """

    def handle_deprecated_params_order(fn):

        @wraps(fn)
        def wrapper(self, *args, **kwargs):

            if is_called_with_old_sig and is_called_with_old_sig(*args, **kwargs):
                warnings.warn("please check and use the new signature of method "+fullname(fn), H2ODeprecationWarning, 2)
                for i, arg in enumerate(args):
                    kw = old_sig[i]
                    kwargs[kw] = arg
                return fn(self, **kwargs)
            else:
                return fn(self, *args, **kwargs)

        return wrapper

    return handle_deprecated_params_order


class MetaFeature(object):
    """To be implemented by meta features exposed through the ``H2OMeta` metaclass"""

    NOT_FOUND = object()

    @classmethod
    def before_class(cls, bases, dct):
        """Allows to dynamically change how the class will be constructed"""
        return bases, dct

    @classmethod
    def after_class(cls, clz):
        """Allows to modify the class after construction.
        Note that decorators applied at class level are still not accessible at that time
        as they're applied only once the class is FULLY constructed."""
        return clz

    @classmethod
    def get_class_attr(cls, clz, name):
        """Allows to override how the class attributes are accessed on this class."""
        return MetaFeature.NOT_FOUND

    @classmethod
    def set_class_attr(cls, clz, name, value):
        """Allows to override how the class attributes are set on this class."""
        return False

    @staticmethod
    def type_attr(clz, name):
        try:
            return type.__getattribute__(clz, name)
        except AttributeError:
            return None


class _Alias(MetaFeature):
    """
    Decorator to alias the current method without having to implement any duplicating or forwarding code.

   :example::
    class Foo(metaclass=H2OMeta):

        @alias('ein', 'uno')
        def one(self, param):
            ...
            do_sth()

    """

    @classmethod
    def before_class(cls, bases, dct):
        attr_names = set(dct)
        ddct = dict(dct)
        for name, impl in dct.items():
            if hasattr(impl, '_aliases'):
                for alias in impl._aliases - attr_names:
                    ddct[str(alias)] = impl
                delattr(impl, '_aliases')
        return bases, ddct

    def __init__(self, *aliases):
        """
        :param aliases: alternative names for the method on which the decorator is applied.
        """
        self._aliases = set(aliases)

    def __call__(self, fn):
        fn._aliases = self._aliases
        return fn
    
    
alias = _Alias


class _BackwardsCompatible(MetaFeature):
    """
    Decorator to keep backward compatibility support for old methods without exposing them (non-discoverable, non-documented).

    :example:
        @backwards_compatibility(
            class_attrs=dict(
              counter=1
            ),
            instance_attrs=dict(
              getincr=local_function_with_legacy_logic
            )
        )
        class Foo(metaclass=H2OMeta):
            global_counter = 0

            def __init__(self):
                self._counter = 0

            def incr_and_get(self):
                Foo.counter += 1
                self._counter += 1
                return self._counter
    """

    def __init__(self, class_attrs=None, instance_attrs=None):
        self._class_attrs = class_attrs or {}
        self._instance_attrs = instance_attrs or {}

    def __call__(self, clz):
        clz._bc = self
        new_clz = None

        @wraps(clz.__init__)
        def __init__(self, *args, **kwargs):
            super(new_clz, self).__init__(*args, **kwargs)
            self._bci = {name: val.__get__(self, new_clz) if callable(val) else val for name, val in clz._bc._instance_attrs.items()}

        def __getattr__(self, name):
            try:
                attr = super(new_clz, self).__getattr__(self, name)
                return attr
            except AttributeError:
                pass
            if name in self._bci:
                return self._bci[name]
            return getattr(new_clz, name)

        new_clz = extend_and_replace(clz, __init__=__init__, __getattr__=__getattr__)
        return new_clz

    @classmethod
    def get_class_attr(cls, clz, name):
        bc = cls.type_attr(clz, '_bc')
        if bc is not None and name in bc._class_attrs:
            return bc._class_attrs[name]
        return super(_BackwardsCompatible, cls).get_class_attr(clz, name)

    @classmethod
    def set_class_attr(cls, clz, name, value):
        bc = cls.type_attr(clz, '_bc')
        if bc is not None and name in bc._class_attrs:
            bc._class_attrs[name] = value
            return True
        return super(_BackwardsCompatible, cls).set_class_attr(clz, name, value)


backwards_compatibility = _BackwardsCompatible


class H2OMeta(type):
    """
    The H2O metaclass to be used by classes wanting to benefit from most of the decorators implemented in this file.
    Features requiring usage of this metaclass are listed and injected through the `_FEATURES` static field.
    """

    _FEATURES = [_Alias, _BackwardsCompatible]

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
