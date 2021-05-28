import copy
from importlib import import_module


def mixin(obj, *mixins):
    """
    Function adding one or more mixin class to the list of parent classes of the current object.
    It's the safest and recommended way to apply mixins as it just adds base classes to the class hierarchy of the object.

    :param obj: the object on which to apply the mixins.
    :param mixins: the list of mixin classes to add to the object.
    :return: the extended object.
    """
    if not mixins:
        return obj
    cls = type(obj.__class__.__name__, (obj.__class__,)+tuple(mixins), dict())
    cls.__module__ = obj.__class__.__module__
    obj.__class__ = cls
    return obj


class Mixin:
    """
    Context manager used to temporarily add mixins to an object.
    """

    def __init__(self, obj, *mixins):
        """
        :param obj: the object on which the mixins are temporarily added.
        :param mixins: the list of mixins to apply.
        """
        self._inst = mixin(copy.copy(obj), *mixins)  # no deepcopy necessary, we just want to ensure that the mixin methods are not added to original instance

    def __enter__(self):
        if hasattr(self._inst, '__enter__'):
            return self._inst.__enter__()
        return self._inst

    def __exit__(self, *args):
        if hasattr(self._inst, '__exit__'):
            self._inst.__exit__()


def load_ext(name):
    mod, cls = name.rsplit('.', 1)
    module = import_module(mod)
    return getattr(module, cls)


def assign(obj, objs, deepcopy=False, reserved=False, filtr=None):
    """
    Copies (shallow) all the instance properties from objs to obj.
    The last property added takes precedence over the previous ones.
    For example, when applying ``assign(obj, ext1, ext2)``: 
    if all `obj`, `ext1` and `ext2` contain the property `prop` then the final object will be assigned `prop` from `ext2`.
    :param obj: the instance that will receive the properties from others.
    :param objs: the instances whose properties will be added to the first instance.
    :param deepcopy: True if the added properties should be deep copied (default: False).
    :param reserved: True if reserved properties (e.g. __dict__, __doc__, ...) should also be assigned (default: False).
    :param filtr: function fn(key, value) returning True for the properties that should be assigned.
        If None (default), then all are assigned.
    :return: the modified instance `obj`.
    """
    _filtr = (lambda k, v: not _is_reserved_name(k) and (filtr is None or filtr(k, v))) if not reserved else filtr
    objs = objs if isinstance(objs, (list, tuple)) else [objs]
    for o in objs:
        props = o.__dict__
        if _filtr:
            props = {k: v for k, v in props.items() if _filtr(k, v)}
        if deepcopy:
            props = copy.deepcopy(props)
        for k, v in props.items():
            setattr(obj, k, v)
    return obj
    
    
def rebind(obj, *mixins):
    """
    inspect the methods from each mixin and bind them each to the object.
    """
    for m in reversed(mixins): 
        for name in m.__dict__:
            if _is_reserved_name(name): continue
            if not callable(m.__dict__[name]): continue
            obj.__dict__[name] = m.__dict__[name].__get__(obj)


def _is_reserved_name(name):
    return name.startswith("__") and name.endswith("__")

