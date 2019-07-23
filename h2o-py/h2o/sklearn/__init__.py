import inspect
import sys

from sklearn.base import ClassifierMixin, RegressorMixin

from .. import automl
from .. import estimators
from .. import transforms
from .wrapper import estimator, params_as_h2o_frames, transformer

module = sys.modules[__name__]


def make_default_params(cls):
    if hasattr(cls, 'param_names'):  # for subclasses of H2OEstimator
        return {k: None for k in cls.param_names}
    return None


def make_estimator(cls, name=None):
    if name is None:
        name = cls.__name__.replace('Estimator', '') + 'Estimator'
    return estimator(cls, name=name, module=module.__name__,
                     default_params=make_default_params(cls),
                     is_generic=True)


def make_classifier(cls, name=None):
    if name is None:
        name = cls.__name__.replace('Estimator', '') + 'Classifier'
    return estimator(cls, name=name, module=module.__name__,
                     default_params=make_default_params(cls),
                     mixins=(ClassifierMixin,),
                     )


def make_regressor(cls, name=None):
    if name is None:
        name = cls.__name__.replace('Estimator', '') + 'Regressor'
    return estimator(cls, name=name, module=module.__name__,
                     default_params=make_default_params(cls),
                     mixins=(RegressorMixin,),
                     )


def make_transformer(cls, name=None):
    if name is None:
        name = cls.__name__
    return transformer(cls, name=name, module=module.__name__,
                       default_params=make_default_params(cls),
                       )


gen_estimators = []
for mod in [automl, estimators]:
    for name, cls in inspect.getmembers(mod, inspect.isclass):
        if name in ['H2OEstimator']:
            continue
        gen_estimators.append(make_estimator(cls))
        gen_estimators.append(make_classifier(cls))
        gen_estimators.append(make_regressor(cls))

for mod in [transforms]:
    for name, cls in inspect.getmembers(mod, inspect.isclass):
        if name in ['H2OTransformer']:
            continue
        gen_estimators.append(make_transformer(cls))

__all__ = []
for e in gen_estimators:
    setattr(module, e.__name__, e)
    __all__.append(e.__name__)
