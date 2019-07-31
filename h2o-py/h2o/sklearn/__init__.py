import inspect
import sys

from sklearn.base import ClassifierMixin, RegressorMixin

from .. import automl
from .. import estimators
from .. import transforms
from .wrapper import estimator, params_as_h2o_frames, register_module, transformer, H2OConnectionMonitorMixin

module = sys.modules[__name__]


def _register_submodule(name=None):
    """
    If name is provided, this registers and attaches a new submodule to the current module.
    :param name: the name of the submodule or None if no submodule is used
    :return: the module name for the (newly) registered submodule
    """
    mod_name = __name__
    if name:
        mod_name = '.'.join([mod_name, name])
        mod = register_module(mod_name)
        setattr(module, name, mod)
    return mod_name


def _make_default_params(cls):
    if hasattr(cls, 'param_names'):  # for subclasses of H2OEstimator
        return {k: None for k in cls.param_names}
    return None


def make_estimator(cls, name=None, submodule=None):
    if name is None:
        name = cls.__name__.replace('Estimator', '') + 'Estimator'
    return estimator(cls, name=name, module=_register_submodule(submodule),
                     default_params=_make_default_params(cls),
                     is_generic=True)


def make_classifier(cls, name=None, submodule=None):
    if name is None:
        name = cls.__name__.replace('Estimator', '') + 'Classifier'
    return estimator(cls, name=name, module=_register_submodule(submodule),
                     default_params=_make_default_params(cls),
                     mixins=(ClassifierMixin,),
                     )


def make_regressor(cls, name=None, submodule=None):
    if name is None:
        name = cls.__name__.replace('Estimator', '') + 'Regressor'
    return estimator(cls, name=name, module=_register_submodule(submodule),
                     default_params=_make_default_params(cls),
                     mixins=(RegressorMixin,),
                     )


def make_transformer(cls, name=None, submodule=None):
    if name is None:
        name = cls.__name__
    return transformer(cls, name=name, module=_register_submodule(submodule),
                       default_params=_make_default_params(cls),
                       )


def h2o_connection(**init_args):
    conn_monitor = H2OConnectionMonitorMixin()
    conn_monitor._init_connection_args = init_args
    return conn_monitor


gen_estimators = []
for mod in [automl, estimators]:
    submodule = mod.__name__.split('.')[-1]
    for name, cls in inspect.getmembers(mod, inspect.isclass):
        if name in ['H2OEstimator']:
            continue
        gen_estimators.append(make_estimator(cls, submodule=submodule))
        gen_estimators.append(make_classifier(cls, submodule=submodule))
        gen_estimators.append(make_regressor(cls, submodule=submodule))

for mod in [transforms]:
    submodule = mod.__name__.split('.')[-1]
    for name, cls in inspect.getmembers(mod, inspect.isclass):
        if name in ['H2OTransformer']:
            continue
        gen_estimators.append(make_transformer(cls, submodule=submodule))

__all__ = set('h2o_connection')
# exposes all the generated estimators in current module for ease of use.
# for clarity, it is still possible to import them from submodules
for e in gen_estimators:
    setattr(module, e.__name__, e)
    __all__.add(e.__name__)
__all__ = sorted(list(__all__))
