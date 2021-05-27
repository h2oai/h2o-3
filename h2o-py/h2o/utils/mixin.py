import copy
from importlib import import_module


def load_ext(name):
    mod, cls = name.rsplit('.', 1)
    module = import_module(mod)
    return getattr(module, cls)
    
    
def rebind(obj, *mixins):
    """
    inspect the methods from each mixin and bind them each to the object.
    """
    for m in reversed(mixins):
        for name in m.__dict__:
            if name.startswith("__") and name.endswith("__"): continue
            if not callable(m.__dict__[name]): continue
            obj.__dict__[name] = m.__dict__[name].__get__(obj)


def mixin(obj, *mixins):
    """
    Function adding one or more mixin class to the list of parent classes of the current object.
    It's the safest and recommended way to apply mixins as it just adds metaclasses to the class hierarchy of the object.

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
        self._inst = mixin(copy.copy(obj), *mixins)  # no deepcopy necessary, we just want to ensure mixin methods are not added to original instance

    def __enter__(self):
        if hasattr(self._inst, '__enter__'):
            return self._inst.__enter__()
        return self._inst

    def __exit__(self, *args):
        if hasattr(self._inst, '__exit__'):
            self._inst.__exit__()

