import inspect
import sys

from sklearn.base import ClassifierMixin, RegressorMixin

from .. import automl
from .. import estimators
from .wrapper import estimator, expect_h2o_frames

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
                     mixins=(ClassifierMixin,), )


def make_regressor(cls, name=None):
    if name is None:
        name = cls.__name__.replace('Estimator', '') + 'Regressor'
    return estimator(cls, name=name, module=module.__name__,
                     default_params=make_default_params(cls),
                     mixins=(RegressorMixin,), )


gen_estimators = []
for mod in [automl, estimators]:
    for name, cls in inspect.getmembers(mod, inspect.isclass):
        if name in ['H2OEstimator']:
            continue
        gen_estimators.append(make_estimator(cls))
        gen_estimators.append(make_classifier(cls))
        gen_estimators.append(make_regressor(cls))


__all__ = []
for estimator in gen_estimators:
    setattr(module, estimator.__name__, estimator)
    __all__.append(estimator.__name__)
