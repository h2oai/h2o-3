import copy
from importlib import import_module
import sys
import types


__mixin_classes_cache__ = {}


def mixin(target, *mixins):
    """
    Function adding one or more mixin class to the list of parent classes of the current object.
    It's the safest and recommended way to apply mixins as it just adds base classes to the class hierarchy of the object.

    :param target: the object on which to apply the mixins.
    :param mixins: the list of mixin classes to add to the object.
    :return: the extended object.
    """
    mro = target.__class__.mro()
    mixins = filter(lambda m: m and m not in mro, mixins)
    if not mixins:
        return target
    bases = (target.__class__,) + tuple(mixins)
    cls = __mixin_classes_cache__.get(bases, None)
    if cls is None:
        cls = type(target.__class__.__name__, bases, dict())
        cls.__module__ = target.__class__.__module__
        __mixin_classes_cache__[bases] = cls
    target.__class__ = cls
    return target


def register_module(module_name, module=None):
    """
    Creates and globally registers a module with given name.

    :param module_name: the name of the module to register.
    :param module: the optional module to register to the given name, if not specified then a new empty module is created.
    :return: the module with given name.
    """
    if module_name not in sys.modules:
        mod = module or types.ModuleType(module_name)
        sys.modules[module_name] = mod
    return sys.modules[module_name]


def register_submodule(parent, name, module=None):
    """
    This registers and attaches a new submodule to the parent module.
    :param parent: the parent module to which the submodule will be created and attached. 
    :param name: the name of the submodule or None if no submodule is used.
    :param module: the optional submodule to register, if not specified then a new empty module is created
    :return: the module name for the (newly) registered submodule.
    """
    mod_name = '.'.join([parent.__name__, name])
    mod = register_module(mod_name, module=module)
    setattr(parent, name, mod)
    return mod_name


def register_class(cls):
    """
    Register a class' module, and adds it to its module.

    :param cls: the class to register.
    """
    module = register_module(cls.__module__)
    setattr(module, cls.__name__, cls)


class Mixin:
    """
    Context manager used to temporarily add mixins to an object.
    """

    def __init__(self, target, *mixins):
        """
        :param target: the object on which the mixins are temporarily added.
        :param mixins: the list of mixins to apply.
        """
        self._inst = mixin(copy.copy(target), *mixins)  # no deepcopy necessary, we just want to ensure that the mixin methods are not added to original instance

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


def assign(target, objs, deepcopy=False, reserved=False, predicate=None):
    """
    Copies (shallow) all the instance properties from objs to obj.
    The last property added takes precedence over the previous ones.
    For example, when applying ``assign(obj, ext1, ext2)``: 
    if all `obj`, `ext1` and `ext2` contain the property `prop` then the final object will be assigned `prop` from `ext2`.
    :param target: the instance that will receive the properties from others.
    :param objs: the instances whose properties will be added to the first instance.
    :param deepcopy: True if the added properties should be deep copied (default: False).
    :param reserved: True if reserved properties (e.g. __dict__, __doc__, ...) should also be assigned (default: False).
    :param predicate: function fn(key, value) returning True for the properties that should be assigned.
        If None (default), then all are assigned.
    :return: the modified instance `obj`.
    """
    _filter = (lambda k, v: not _is_reserved_name(k) and (predicate is None or predicate(k, v))) if not reserved else predicate
    objs = filter(None, objs if isinstance(objs, (list, tuple)) else [objs])
    for o in objs:
        props = vars(o)
        if _filter:
            props = {k: v for k, v in props.items() if _filter(k, v)}
        if deepcopy:
            props = copy.deepcopy(props)
        for k, v in props.items():
            setattr(target, k, v)
    return target
    
    
def rebind(target, *mixins):
    """
    inspect the methods from each mixin and bind them each to the target object.
    """
    mixins = filter(None, mixins)
    for m in reversed(mixins): 
        for name in vars(m):
            if _is_reserved_name(name): continue
            attr = getattr(m, name)
            if not callable(attr): continue
            setattr(target, name, attr.__get__(target))


def _is_reserved_name(name):
    return name.startswith("__") and name.endswith("__")

